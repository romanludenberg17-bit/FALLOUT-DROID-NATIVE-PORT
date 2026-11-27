// libq.so - LAN co-op shim for the prebuilt 4NMP engine.
//
// The engine finds its own IPv4 with gethostname() + getaddrinfo(). On Android
// the host name is "localhost", so LAN sessions were advertised on 127.0.0.1.
// This library takes the place of libz.so in libmain.so's dependency list
// (and depends on the real libz.so), so its gethostname() is found before
// libc's. It returns the Wi-Fi / hotspot / VPN address as text, which the
// engine's getaddrinfo() then resolves to that address.
//
// Address choice: --gta4_lan_ipv4=<ip> (last one in args.txt, written by the
// MP hub for ZeroTier/Tailscale) > REX_LAN_IPV4 > wlan/ap/swlan/eth/p2p.
// The constructor also takes a WifiManager.MulticastLock so multicast session
// discovery reaches the game while the screen is on.
#include <arpa/inet.h>
#include <dlfcn.h>
#include <errno.h>
#include <ifaddrs.h>
#include <jni.h>
#include <net/if.h>
#include <netinet/in.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <signal.h>
#include <sys/mman.h>
#include <ucontext.h>
#include <time.h>
#include <ctype.h>
#include <dirent.h>
#include <android/log.h>

#define TAG "gta4-lanshim"

static int private_v4(uint32_t host) {
  return (host >> 24) == 10 || (host >> 20) == 0xAC1 || (host >> 16) == 0xC0A8 ||
         (host >> 22) == (100u << 2 | 1);  // 100.64.0.0/10 (Tailscale / CGNAT)
}

static int args_override(char* out, size_t len) {
  char cmd[128] = {0};
  FILE* f = fopen("/proc/self/cmdline", "r");
  if (!f) return 0;
  size_t n = fread(cmd, 1, sizeof(cmd) - 1, f);
  fclose(f);
  cmd[n] = 0;
  char* colon = strchr(cmd, ':');
  if (colon) *colon = 0;
  char path[256];
  snprintf(path, sizeof(path), "/storage/emulated/0/Android/data/%s/files/args.txt", cmd);
  f = fopen(path, "r");
  if (!f) return 0;
  char line[256], found[64] = {0};
  while (fgets(line, sizeof(line), f)) {
    if (strncmp(line, "--gta4_lan_ipv4=", 16)) continue;
    char* v = line + 16;
    v[strcspn(v, "\r\n ")] = 0;
    snprintf(found, sizeof(found), "%s", v);  // the last line wins, empty clears
  }
  fclose(f);
  struct in_addr a;
  if (!found[0] || inet_pton(AF_INET, found, &a) != 1) return 0;
  snprintf(out, len, "%s", found);
  return 1;
}

static int pick_address(char* out, size_t len) {
  if (args_override(out, len)) return 1;
  const char* env = getenv("REX_LAN_IPV4");
  struct in_addr a;
  if (env && inet_pton(AF_INET, env, &a) == 1) { snprintf(out, len, "%s", env); return 1; }
  struct ifaddrs* list = NULL;
  if (getifaddrs(&list) != 0) return 0;
  int best_rank = -100;
  uint32_t best = 0;
  for (struct ifaddrs* it = list; it; it = it->ifa_next) {
    if (!it->ifa_addr || it->ifa_addr->sa_family != AF_INET) continue;
    if (!(it->ifa_flags & IFF_UP) || (it->ifa_flags & IFF_LOOPBACK)) continue;
    uint32_t addr = ((struct sockaddr_in*)it->ifa_addr)->sin_addr.s_addr;
    const char* n = it->ifa_name ? it->ifa_name : "";
    int rank = private_v4(ntohl(addr)) ? 1 : 0;
    if (!strncmp(n, "wlan", 4) || !strncmp(n, "ap", 2) || !strncmp(n, "swlan", 5) ||
        !strncmp(n, "softap", 6) || !strncmp(n, "eth", 3) || !strncmp(n, "p2p", 3)) rank += 4;
    if (!strncmp(n, "rmnet", 5) || !strncmp(n, "ccmni", 5) || !strncmp(n, "dummy", 5)) rank -= 4;
    if (rank > best_rank) { best_rank = rank; best = addr; }
  }
  freeifaddrs(list);
  if (!best) return 0;
  struct in_addr b = {.s_addr = best};
  return inet_ntop(AF_INET, &b, out, (socklen_t)len) != NULL;
}

__attribute__((visibility("default"))) int gethostname(char* name, size_t len) {
  if (!name || !len) { errno = EINVAL; return -1; }
  char ip[64];
  if (pick_address(ip, sizeof(ip))) {
    if (strlen(ip) + 1 > len) { errno = ENAMETOOLONG; return -1; }
    memcpy(name, ip, strlen(ip) + 1);
    __android_log_print(ANDROID_LOG_INFO, TAG, "LAN address %s", ip);
    return 0;
  }
  typedef int (*real_t)(char*, size_t);
  real_t real = (real_t)dlsym(RTLD_NEXT, "gethostname");
  return real ? real(name, len) : -1;
}

// ---- MulticastLock (best effort) ----
typedef jint (*GetVMs)(JavaVM**, jsize, jsize*);

static void* take_multicast_lock(void* unused) {
  (void)unused;
  GetVMs get = NULL;
  const char* libs[] = {"libnativehelper.so", "libart.so"};
  for (int i = 0; i < 2 && !get; ++i) {
    void* h = dlopen(libs[i], RTLD_NOW | RTLD_NOLOAD);
    if (h) get = (GetVMs)dlsym(h, "JNI_GetCreatedJavaVMs");
  }
  JavaVM* vm = NULL;
  jsize count = 0;
  if (!get || get(&vm, 1, &count) != JNI_OK || count < 1 || !vm) return NULL;
  JNIEnv* env = NULL;
  if ((*vm)->AttachCurrentThread(vm, &env, NULL) != JNI_OK || !env) return NULL;
  jclass at = (*env)->FindClass(env, "android/app/ActivityThread");
  jmethodID cur = at ? (*env)->GetStaticMethodID(env, at, "currentApplication", "()Landroid/app/Application;") : NULL;
  jobject app = cur ? (*env)->CallStaticObjectMethod(env, at, cur) : NULL;
  if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
  if (app) {
    jclass ctx = (*env)->FindClass(env, "android/content/Context");
    jmethodID svc = (*env)->GetMethodID(env, ctx, "getSystemService", "(Ljava/lang/String;)Ljava/lang/Object;");
    jobject wifi = (*env)->CallObjectMethod(env, app, svc, (*env)->NewStringUTF(env, "wifi"));
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    if (wifi) {
      jclass wm = (*env)->FindClass(env, "android/net/wifi/WifiManager");
      jmethodID create = (*env)->GetMethodID(env, wm, "createMulticastLock",
          "(Ljava/lang/String;)Landroid/net/wifi/WifiManager$MulticastLock;");
      jobject lock = (*env)->CallObjectMethod(env, wifi, create, (*env)->NewStringUTF(env, "gta4-coop"));
      if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
      if (lock) {
        jclass lc = (*env)->GetObjectClass(env, lock);
        (*env)->CallVoidMethod(env, lock, (*env)->GetMethodID(env, lc, "setReferenceCounted", "(Z)V"), JNI_FALSE);
        (*env)->CallVoidMethod(env, lock, (*env)->GetMethodID(env, lc, "acquire", "()V"));
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        else { (*env)->NewGlobalRef(env, lock); __android_log_print(ANDROID_LOG_INFO, TAG, "multicast lock held"); }
      }
    }
  }
  (*vm)->DetachCurrentThread(vm);
  return NULL;
}

// One-time: GTA IV Native 1.1.x wrote LibertyRecomp/touch-controls.ini with
// its own control numbering; the 4NMP engine would misplace its buttons with
// it. Move it aside once so the 4NMP layout (and the user's later edits) apply.
static void reset_foreign_touch_layout(void) {
  char cmd[128] = {0};
  FILE* f = fopen("/proc/self/cmdline", "r");
  if (!f) return;
  size_t n = fread(cmd, 1, sizeof(cmd) - 1, f);
  fclose(f);
  cmd[n] = 0;
  char* colon = strchr(cmd, ':');
  if (colon) *colon = 0;
  char dir[256], marker[320], ini[320], backup[352];
  snprintf(dir, sizeof(dir), "/storage/emulated/0/Android/data/%s/files/LibertyRecomp", cmd);
  snprintf(marker, sizeof(marker), "%s/.v504-controls", dir);
  if (access(marker, F_OK) == 0) return;
  snprintf(ini, sizeof(ini), "%s/touch-controls.ini", dir);
  snprintf(backup, sizeof(backup), "%s/touch-controls.ini.gta3-layout", dir);
  if (access(ini, F_OK) == 0 && rename(ini, backup) == 0)
    __android_log_print(ANDROID_LOG_INFO, TAG, "old touch layout moved to %s", backup);
  FILE* m = fopen(marker, "w");
  if (m) fclose(m);
}

// ---- "Game completed": install the bundled 100% save once ----
// The MP hub's "Игра пройдена" button appends --gta4_complete_save=1 to
// args.txt and starts the game. Before the engine looks at its saves, the
// bundled Resources/saves/<file> is copied into every save folder the
// launcher uses (an existing file is kept as .bak), then the line is removed.
static int package_name(char* out, size_t len) {
  FILE* f = fopen("/proc/self/cmdline", "r");
  if (!f) return 0;
  size_t n = fread(out, 1, len - 1, f);
  fclose(f);
  out[n] = 0;
  char* colon = strchr(out, ':');
  if (colon) *colon = 0;
  return out[0] != 0;
}

static int same_content(const char* a, const char* b) {
  FILE* x = fopen(a, "rb");
  FILE* y = fopen(b, "rb");
  int same = x && y;
  char p[16384], q[16384];
  while (same) {
    size_t n = fread(p, 1, sizeof(p), x), m = fread(q, 1, sizeof(q), y);
    if (n != m || memcmp(p, q, n)) same = 0;
    if (!n) break;
  }
  if (x) fclose(x);
  if (y) fclose(y);
  return same;
}

static int copy_file(const char* from, const char* to) {
  FILE* in = fopen(from, "rb");
  if (!in) return 0;
  char tmp[600];
  snprintf(tmp, sizeof(tmp), "%s.tmp", to);
  FILE* out = fopen(tmp, "wb");
  if (!out) { fclose(in); return 0; }
  char buf[65536];
  size_t n;
  int ok = 1;
  while ((n = fread(buf, 1, sizeof(buf), in)) > 0) ok &= fwrite(buf, 1, n, out) == n;
  fclose(in);
  ok &= fclose(out) == 0;
  if (!ok) { unlink(tmp); return 0; }
  char bak[620];
  snprintf(bak, sizeof(bak), "%s.bak", to);
  // Back up the user's own file once; never back up a file this shim wrote.
  if (access(to, F_OK) == 0) {
    if (access(bak, F_OK) != 0 && !same_content(tmp, to)) rename(to, bak);
    else unlink(to);
  }
  return rename(tmp, to) == 0;
}

static void mkdirs(const char* dir) {
  char p[512];
  snprintf(p, sizeof(p), "%s", dir);
  for (char* c = p + 1; *c; ++c) if (*c == '/') { *c = 0; mkdir(p, 0775); *c = '/'; }
  mkdir(p, 0775);
}

// <selected game folder>/../save (SharedPreferences "cache_location"/"game").
static int portable_save_dir(const char* pkg, char* out, size_t len) {
  char prefs[300];
  snprintf(prefs, sizeof(prefs), "/data/data/%s/shared_prefs/cache_location.xml", pkg);
  FILE* f = fopen(prefs, "r");
  if (!f) return 0;
  char xml[4096] = {0};
  size_t n = fread(xml, 1, sizeof(xml) - 1, f);
  fclose(f);
  xml[n] = 0;
  static const char key[] = "name=\"game\">";
  char* at = strstr(xml, key);
  if (!at) return 0;
  at += sizeof(key) - 1;
  char* end = strchr(at, '<');
  if (!end || end - at >= 300) return 0;
  char game[300];
  snprintf(game, sizeof(game), "%.*s", (int)(end - at), at);
  char* slash = strrchr(game, '/');
  if (!slash) return 0;
  *slash = 0;
  snprintf(out, len, "%s/save", game);
  return 1;
}

// Put the user's own file back (from .bak) where the 100% save was installed.
static void undo_installed_save(const char* dir, const char* name) {
  char path[500], bak[520];
  snprintf(path, sizeof(path), "%s/%s", dir, name);
  snprintf(bak, sizeof(bak), "%s/%s.bak", dir, name);
  if (access(bak, F_OK) == 0) {
    if (rename(bak, path) == 0) __android_log_print(ANDROID_LOG_INFO, TAG, "restored %s", path);
  } else if (access(path, F_OK) == 0) {
    // No backup: remove it only if it is exactly the bundled 100% save.
    struct stat st;
    if (stat(path, &st) == 0 && (st.st_size == 707822 || st.st_size == 790528)) {
      unlink(path);
      __android_log_print(ANDROID_LOG_INFO, TAG, "removed installed save %s", path);
    }
  }
}

// ---- Portable save layout used by the engine (one folder per save):
//   <save>/<XUID>/545407F2/00000001/<NAME>/<NAME>           raw SAVEBLOCK data
//   <save>/<XUID>/545407F2/00000001/<NAME>/__thumbnail.png
//   <save>/<XUID>/545407F2/Headers/00000001/<NAME>.header   XCONTENT_AGGREGATE_DATA (0x148)
static int is_hex_id(const char* n) {
  if (strlen(n) != 16) return 0;
  for (const char* c = n; *c; ++c) if (!isxdigit((unsigned char)*c)) return 0;
  return 1;
}

static int is_stfs(const char* path) {
  FILE* f = fopen(path, "rb");
  if (!f) return 0;
  char m[4] = {0};
  size_t n = fread(m, 1, 4, f);
  fclose(f);
  return n == 4 && (!memcmp(m, "CON ", 4) || !memcmp(m, "LIVE", 4) || !memcmp(m, "PIRS", 4));
}

// Moves a file into <save>/../save-quarantine instead of deleting it.
static void quarantine(const char* portable_dir, const char* path, const char* tag) {
  char q[460], to[700];
  snprintf(q, sizeof(q), "%s-quarantine", portable_dir);
  mkdirs(q);
  const char* base = strrchr(path, '/');
  snprintf(to, sizeof(to), "%s/%s-%s", q, tag, base ? base + 1 : path);
  if (rename(path, to) != 0) unlink(path);
  __android_log_print(ANDROID_LOG_INFO, TAG, "quarantined %s", path);
}

static void repair_portable_saves(const char* portable_dir) {
  DIR* d = opendir(portable_dir);
  if (!d) return;
  struct dirent* e;
  while ((e = readdir(d))) {
    if (!is_hex_id(e->d_name)) continue;
    char data[600], hdr[600];
    snprintf(data, sizeof(data), "%s/%s/545407F2/00000001", portable_dir, e->d_name);
    snprintf(hdr, sizeof(hdr), "%s/%s/545407F2/Headers/00000001", portable_dir, e->d_name);
    // flat STFS packages directly in the data folder (1.2.6 import source)
    DIR* s = opendir(data);
    if (s) {
      struct dirent* f;
      while ((f = readdir(s))) {
        if (f->d_name[0] == '.') continue;
        char p[800];
        snprintf(p, sizeof(p), "%s/%s", data, f->d_name);
        struct stat st;
        if (stat(p, &st) != 0) continue;
        if (S_ISREG(st.st_mode) && is_stfs(p)) { quarantine(portable_dir, p, e->d_name); continue; }
        if (!S_ISDIR(st.st_mode)) continue;
        // STFS packages inside a save folder where raw data belongs
        DIR* sl = opendir(p);
        if (!sl) continue;
        struct dirent* g;
        while ((g = readdir(sl))) {
          if (g->d_name[0] == '.') continue;
          char q[1000];
          snprintf(q, sizeof(q), "%s/%s", p, g->d_name);
          if (is_stfs(q)) quarantine(portable_dir, q, e->d_name);
        }
        closedir(sl);
      }
      closedir(s);
    }
    DIR* h = opendir(hdr);
    if (h) {
      struct dirent* f;
      while ((f = readdir(h))) {
        if (f->d_name[0] == '.') continue;
        char p[800];
        snprintf(p, sizeof(p), "%s/%s", hdr, f->d_name);
        if (is_stfs(p)) quarantine(portable_dir, p, e->d_name);
      }
      closedir(h);
    }
    // a header without its save folder (e.g. the package above was its data)
    snprintf(data, sizeof(data), "%s/%s/545407F2/00000001/SGTA400/SGTA400", portable_dir, e->d_name);
    snprintf(hdr, sizeof(hdr), "%s/%s/545407F2/Headers/00000001/SGTA400.header", portable_dir, e->d_name);
    if (access(hdr, F_OK) == 0 && access(data, F_OK) != 0) quarantine(portable_dir, hdr, e->d_name);
  }
  closedir(d);
}

static int read_all(const char* path, unsigned char* buf, size_t cap) {
  FILE* f = fopen(path, "rb");
  if (!f) return -1;
  int n = (int)fread(buf, 1, cap, f);
  fclose(f);
  return n;
}

static int write_all(const char* path, const unsigned char* buf, size_t n) {
  FILE* f = fopen(path, "wb");
  if (!f) return 0;
  int ok = fwrite(buf, 1, n, f) == n;
  return fclose(f) == 0 && ok;
}

// Header for <name>: clone another save's header of the same profile (same
// device, XUID and title) and swap the file name; otherwise build one.
static int make_header(const char* hdr_dir, const char* xuid, const char* name, unsigned char out[0x148]) {
  DIR* h = opendir(hdr_dir);
  if (h) {
    struct dirent* f;
    while ((f = readdir(h))) {
      size_t l = strlen(f->d_name);
      if (l != 7 + 7 || strcmp(f->d_name + 7, ".header") || !strncmp(f->d_name, name, 7)) continue;
      char p[800];
      snprintf(p, sizeof(p), "%s/%s", hdr_dir, f->d_name);
      if (read_all(p, out, 0x148) != 0x148) continue;
      for (int i = 0; i + 7 <= 0x148; ++i) {
        if (!memcmp(out + i, f->d_name, 7)) { memcpy(out + i, name, 7); closedir(h); return 1; }
      }
    }
    closedir(h);
  }
  memset(out, 0, 0x148);
  out[3] = 1;  // device_id (BE)
  out[7] = 1;  // content_type: saved game (BE)
  static const char display[] = "GTA IV 100%";
  for (int i = 0; display[i]; ++i) out[8 + 2 * i + 1] = (unsigned char)display[i];  // UTF-16BE
  memcpy(out + 0x108, name, strlen(name));
  unsigned long long id = strtoull(xuid, NULL, 16);
  for (int i = 0; i < 8; ++i) out[0x138 + i] = (unsigned char)(id >> (56 - 8 * i));
  static const unsigned char title[4] = {0x54, 0x54, 0x07, 0xF2};
  memcpy(out + 0x140, title, 4);
  return 1;
}

static void install_one(const char* portable_dir, const char* xuid, const char* source, const char* name) {
  char base[600], dir[700], data[800], thumb[800], hdr_dir[700], hdr[800];
  snprintf(base, sizeof(base), "%s/%s/545407F2", portable_dir, xuid);
  snprintf(dir, sizeof(dir), "%s/00000001/%s", base, name);
  snprintf(data, sizeof(data), "%s/%s", dir, name);
  snprintf(hdr_dir, sizeof(hdr_dir), "%s/Headers/00000001", base);
  snprintf(hdr, sizeof(hdr), "%s/%s.header", hdr_dir, name);
  mkdirs(dir);
  mkdirs(hdr_dir);
  int ok = copy_file(source, data);
  // thumbnail: borrow one from another save of this profile
  snprintf(thumb, sizeof(thumb), "%s/__thumbnail.png", dir);
  if (access(thumb, F_OK) != 0) {
    char other[700];
    snprintf(other, sizeof(other), "%s/00000001", base);
    DIR* d = opendir(other);
    if (d) {
      struct dirent* e;
      while ((e = readdir(d))) {
        if (e->d_name[0] == '.' || !strcmp(e->d_name, name)) continue;
        char t[900];
        snprintf(t, sizeof(t), "%s/%s/__thumbnail.png", other, e->d_name);
        if (access(t, R_OK) == 0 && copy_file(t, thumb)) break;
      }
      closedir(d);
    }
  }
  unsigned char h[0x148];
  if (make_header(hdr_dir, xuid, name, h)) ok = write_all(hdr, h, sizeof(h)) && ok;
  __android_log_print(ANDROID_LOG_INFO, TAG, "100%% save -> %s: %s", dir, ok ? "ok" : "failed");
}

static void install_into_profiles(const char* portable_dir, const char* store, const char* source, const char* name) {
  int count = 0;
  // every profile the engine already keeps saves for
  DIR* d = opendir(portable_dir);
  if (d) {
    struct dirent* e;
    while ((e = readdir(d))) {
      if (!is_hex_id(e->d_name) || !strcmp(e->d_name, "E0001BBC781D8027")) continue;
      char hdr[600];
      snprintf(hdr, sizeof(hdr), "%s/%s/545407F2/Headers/00000001", portable_dir, e->d_name);
      if (access(hdr, F_OK) != 0) continue;
      install_one(portable_dir, e->d_name, source, name);
      ++count;
    }
    closedir(d);
  }
  // profiles signed in on this device (LibertyRecomp/saves/545407F2/profile/<XUID>)
  char prof[500];
  snprintf(prof, sizeof(prof), "%s/545407F2/profile", store);
  d = opendir(prof);
  if (d) {
    struct dirent* e;
    while ((e = readdir(d))) {
      if (!is_hex_id(e->d_name)) continue;
      char hdr[700];
      snprintf(hdr, sizeof(hdr), "%s/%s/545407F2/Headers/00000001/%s.header", portable_dir, e->d_name, name);
      if (access(hdr, F_OK) == 0) continue;  // done above
      install_one(portable_dir, e->d_name, source, name);
      ++count;
    }
    closedir(d);
  }
  if (!count) __android_log_print(ANDROID_LOG_WARN, TAG, "no save profile found; 100%% save not installed");
}

static void remove_complete_save(const char* portable_dir, const char* name) {
  DIR* d = opendir(portable_dir);
  if (!d) return;
  struct dirent* e;
  while ((e = readdir(d))) {
    if (!is_hex_id(e->d_name)) continue;
    char dir[700], hdr[700];
    snprintf(dir, sizeof(dir), "%s/%s/545407F2/00000001/%s", portable_dir, e->d_name, name);
    snprintf(hdr, sizeof(hdr), "%s/%s/545407F2/Headers/00000001/%s.header", portable_dir, e->d_name, name);
    char bak[760];
    snprintf(bak, sizeof(bak), "%s/%s.bak", dir, name);
    if (access(bak, F_OK) == 0) { undo_installed_save(dir, name); continue; }  // their own SGTA400
    undo_installed_save(dir, name);
    char t[760];
    snprintf(t, sizeof(t), "%s/__thumbnail.png", dir);
    unlink(t);
    if (rmdir(dir) == 0) unlink(hdr);
  }
  closedir(d);
}

static int g_args_fresh;

static void install_complete_save(void) {
  char pkg[128];
  if (!package_name(pkg, sizeof(pkg))) return;
  char args[300];
  snprintf(args, sizeof(args), "/storage/emulated/0/Android/data/%s/files/args.txt", pkg);
  // Co-op starts append "--gta4_multiplayer_backend=lan" + player name to
  // args.txt and never remove them: every later start then booted in network
  // mode and hung on a black screen. Keep them only for the start that has
  // just written them (args.txt modified seconds ago).
  const int fresh = g_args_fresh;  // measured before this shim touched args.txt
  FILE* f = fopen(args, "r");
  if (!f) return;
  char* lines_buf = NULL;
  size_t lines_len = 0;
  {
    char tmp[512];
    size_t cap2 = 0;
    while (fgets(tmp, sizeof(tmp), f)) {
      size_t l = strlen(tmp);
      if (lines_len + l + 1 > cap2) { cap2 = (cap2 + l + 1) * 2; lines_buf = realloc(lines_buf, cap2); if (!lines_buf) { fclose(f); return; } }
      memcpy(lines_buf + lines_len, tmp, l); lines_len += l; lines_buf[lines_len] = 0;
    }
  }
  fclose(f);
  const int wants_save = lines_buf && (strstr(lines_buf, "--gta4_complete_save=1") || strstr(lines_buf, "--gta4_restore_save=1"));
  const int keep_network = fresh && !wants_save;
  f = fmemopen(lines_buf ? lines_buf : (char*)"", lines_len, "r");
  if (!f) { free(lines_buf); return; }
  char* kept = NULL;
  size_t kept_len = 0, cap = 0;
  int requested = 0, stripped = 0, restore = 0;
  char line[512];
  while (fgets(line, sizeof(line), f)) {
    if (!strncmp(line, "--gta4_complete_save=", 21)) { requested = line[21] == '1'; continue; }
    if (!strncmp(line, "--gta4_restore_save=", 20)) { restore = line[20] == '1'; continue; }
    // 1.2.2 tried these for the vehicle badge boxes; they did not help and cost speed.
    if (!strncmp(line, "--gta4_native_mobile_coverage_free=false", 40) ||
        !strncmp(line, "--gta4_native_mobile_opaque_depth=false", 39) ||
        !strncmp(line, "# Vehicle badges / decals drew a black box", 42) ||
        !strncmp(line, "--gta4_skip_intro=true", 22)) { stripped = 1; continue; }
    if (!keep_network && (!strncmp(line, "--gta4_multiplayer_backend=", 27) || !strncmp(line, "--gta4_player_name=", 19) ||
                          !strncmp(line, "--gta4_lan_ipv4=", 16) || !strncmp(line, "#-gta4_skip_intro=true", 22) ||
                          !strncmp(line, "# LAN co-op (serverless)", 24))) { stripped = 1; continue; }
    size_t l = strlen(line);
    if (kept_len + l + 1 > cap) { cap = (cap + l + 1) * 2; kept = realloc(kept, cap); if (!kept) { fclose(f); return; } }
    memcpy(kept + kept_len, line, l); kept_len += l; kept[kept_len] = 0;
  }
  fclose(f);
  free(lines_buf);
  __android_log_print(ANDROID_LOG_INFO, TAG, "args: network mode %s", keep_network ? "kept (co-op start)" : "off");
  if (requested || stripped || restore) {
    f = fopen(args, "w");  // one shot: drop the request line (and stale lines)
    if (f) { if (kept) fwrite(kept, 1, kept_len, f); fclose(f); }
  }
  free(kept);
  const char* name = "SGTA400";
  char store[400];
  snprintf(store, sizeof(store), "/storage/emulated/0/Android/data/%s/files/LibertyRecomp/saves", pkg);
  undo_installed_save(store, "SGTA412");  // 1.2.2 copied a save here
  char portable_dir[400];
  if (!portable_save_dir(pkg, portable_dir, sizeof(portable_dir))) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "no portable save folder");
    return;
  }
  undo_installed_save(portable_dir, "SGTA412");
  undo_installed_save(portable_dir, name);
  // 1.2.6 put the Xbox package into an import folder; the importer copied the
  // whole STFS package into slots that must hold the raw save -> crash on load.
  repair_portable_saves(portable_dir);
  if (restore) {
    remove_complete_save(portable_dir, name);
    __android_log_print(ANDROID_LOG_INFO, TAG, "own save restored");
    return;
  }
  if (!requested) return;
  char source[300] = {0};
  const char* roots[] = {"/data/user/0/%s/files/Resources/saves/%s", "/data/data/%s/files/Resources/saves/%s",
                         "/storage/emulated/0/Android/data/%s/files/Resources/saves/%s"};
  for (int i = 0; i < 3 && !source[0]; ++i) {
    char c[300];
    snprintf(c, sizeof(c), roots[i], pkg, name);
    // a stale 1.2.6 copy is the STFS package, not the raw save: never install it
    if (access(c, R_OK) == 0 && !is_stfs(c)) snprintf(source, sizeof(source), "%s", c);
  }
  if (!source[0]) { __android_log_print(ANDROID_LOG_WARN, TAG, "bundled 100%% save not found"); return; }
  install_into_profiles(portable_dir, store, source, name);
}

// Keep LibertyRecomp/touch-icons in step with the icons bundled in this APK
// (the launcher unpacks assets/Resources into files/Resources).
static void sync_touch_icons(void) {
  char pkg[128];
  if (!package_name(pkg, sizeof(pkg))) return;
  char src[300] = {0}, dst[300];
  const char* roots[] = {"/data/user/0/%s/files/Resources/touch-icons", "/data/data/%s/files/Resources/touch-icons"};
  for (int i = 0; i < 2 && !src[0]; ++i) {
    char c[300];
    snprintf(c, sizeof(c), roots[i], pkg);
    if (access(c, R_OK) == 0) snprintf(src, sizeof(src), "%s", c);
  }
  if (!src[0]) return;
  snprintf(dst, sizeof(dst), "/storage/emulated/0/Android/data/%s/files/LibertyRecomp/touch-icons", pkg);
  mkdirs(dst);
  DIR* dir = opendir(src);
  if (!dir) return;
  struct dirent* e;
  int copied = 0;
  while ((e = readdir(dir))) {
    const size_t n = strlen(e->d_name);
    if (n < 5 || strcmp(e->d_name + n - 4, ".png")) continue;
    char from[600], to[600];
    snprintf(from, sizeof(from), "%s/%s", src, e->d_name);
    snprintf(to, sizeof(to), "%s/%s", dst, e->d_name);
    struct stat a, b;
    if (stat(from, &a) != 0) continue;
    if (stat(to, &b) == 0 && a.st_size == b.st_size) continue;
    if (copy_file(from, to)) {
      char bak[620];
      snprintf(bak, sizeof(bak), "%s.bak", to);
      unlink(bak);
      ++copied;
    }
  }
  closedir(dir);
  if (copied) __android_log_print(ANDROID_LOG_INFO, TAG, "touch icons updated: %d", copied);
}

// Vehicle badges / decals and mission markers drew black where they should be
// transparent. Keep the Adreno renderer's alpha-sensitive shortcuts off: the
// UBO path for shared constants (alpha test threshold), GPU detiling and the
// inert-blending drop. Lines are appended to args.txt (read on the next start).
static const char* kRenderFixes[] = {
    "--gta4_native_ubo_shared_constants=false",
    "--gta4_native_gpu_detile=false",
    "--gta4_native_drop_inert_blending=false",
};

static void ensure_render_fixes(const char* pkg) {
  char args[300];
  snprintf(args, sizeof(args), "/storage/emulated/0/Android/data/%s/files/args.txt", pkg);
  FILE* f = fopen(args, "r");
  if (!f) return;
  int have[3] = {0, 0, 0};
  char line[512];
  while (fgets(line, sizeof(line), f))
    for (int i = 0; i < 3; ++i)
      if (!strncmp(line, kRenderFixes[i], strlen(kRenderFixes[i]))) have[i] = 1;
  fclose(f);
  if (have[0] && have[1] && have[2]) return;
  if (!(f = fopen(args, "a"))) return;
  fputs("\n# Transparent decals / mission markers (render fixes)\n", f);
  for (int i = 0; i < 3; ++i) if (!have[i]) { fputs(kRenderFixes[i], f); fputc('\n', f); }
  fclose(f);
}

// The converted-texture cache was built by an older build and may hold the
// broken conversions: move it aside once so the textures are rebuilt.
static void reset_texture_cache_once(const char* pkg) {
  char save[400];
  if (!portable_save_dir(pkg, save, sizeof(save))) return;
  char* slash = strrchr(save, '/');
  if (!slash) return;
  *slash = 0;  // <game parent>
  char cache[460], old[480], marker[480];
  snprintf(cache, sizeof(cache), "%s/shader_cache/texture_cache", save);
  snprintf(old, sizeof(old), "%s/shader_cache/texture_cache.old", save);
  snprintf(marker, sizeof(marker), "%s/shader_cache/.texture-cache-v625", save);
  if (access(marker, F_OK) == 0) return;
  if (access(cache, F_OK) == 0 && access(old, F_OK) != 0 && rename(cache, old) == 0)
    __android_log_print(ANDROID_LOG_INFO, TAG, "texture cache moved to %s", old);
  FILE* m = fopen(marker, "w");
  if (m) fclose(m);
}

// ---- Diagnostics in shared storage (Android/data is hidden from file
// managers on Android 11+): Download/GTAIV-Native/ gets the previous run's
// logs and crash reports, args.txt, the selected game folder and a listing of
// the save folder, so they can be sent without adb.
static void list_tree(FILE* out, const char* dir, int depth) {
  DIR* d = opendir(dir);
  if (!d) { fprintf(out, "  [cannot open %s: %s]\n", dir, strerror(errno)); return; }
  struct dirent* e;
  while ((e = readdir(d))) {
    if (!strcmp(e->d_name, ".") || !strcmp(e->d_name, "..")) continue;
    char path[700];
    snprintf(path, sizeof(path), "%s/%s", dir, e->d_name);
    struct stat st;
    if (stat(path, &st) != 0) continue;
    fprintf(out, "%*s%s%s %lld\n", 2 + depth * 2, "", e->d_name, S_ISDIR(st.st_mode) ? "/" : "",
            (long long)st.st_size);
    if (S_ISDIR(st.st_mode) && depth < 5) list_tree(out, path, depth + 1);
  }
  closedir(d);
}

static void copy_tree(const char* from, const char* to, int depth) {
  DIR* d = opendir(from);
  if (!d) return;
  mkdirs(to);
  struct dirent* e;
  while ((e = readdir(d))) {
    if (e->d_name[0] == '.') continue;
    char a[700], b[700];
    snprintf(a, sizeof(a), "%s/%s", from, e->d_name);
    snprintf(b, sizeof(b), "%s/%s", to, e->d_name);
    struct stat st;
    if (stat(a, &st) != 0) continue;
    if (S_ISDIR(st.st_mode)) { if (depth < 3) copy_tree(a, b, depth + 1); }
    else if (st.st_size < 64 * 1024 * 1024) { unlink(b); copy_file(a, b); }
  }
  closedir(d);
}

static void cat_file(FILE* out, const char* title, const char* path) {
  fprintf(out, "\n== %s (%s)\n", title, path);
  FILE* f = fopen(path, "r");
  if (!f) { fprintf(out, "[missing: %s]\n", strerror(errno)); return; }
  char buf[4096];
  size_t n;
  while ((n = fread(buf, 1, sizeof(buf), f)) > 0) fwrite(buf, 1, n, out);
  fclose(f);
}

static void write_diagnostics(const char* pkg) {
  const char* root = "/storage/emulated/0/Download/GTAIV-Native";
  mkdirs(root);
  char ext[300], path[700];
  snprintf(ext, sizeof(ext), "/storage/emulated/0/Android/data/%s/files", pkg);
  // previous run's logs and crash reports
  snprintf(path, sizeof(path), "%s/Liberty Recompiled", ext);
  char dest[400];
  snprintf(dest, sizeof(dest), "%s/previous-run", root);
  copy_tree(path, dest, 0);
  snprintf(path, sizeof(path), "%s/crash_reports", ext);
  snprintf(dest, sizeof(dest), "%s/previous-run/crash_reports", root);
  copy_tree(path, dest, 0);
  char diag[400];
  snprintf(diag, sizeof(diag), "%s/diag.txt", root);
  FILE* out = fopen(diag, "w");
  if (!out) return;
  fprintf(out, "GTA IV Native diagnostics (lanshim)\npackage=%s\n", pkg);
  char ip[64];
  fprintf(out, "lan_address=%s\n", pick_address(ip, sizeof(ip)) ? ip : "(none)");
  snprintf(path, sizeof(path), "/data/data/%s/shared_prefs/cache_location.xml", pkg);
  cat_file(out, "launcher game folder", path);
  char save[400];
  if (portable_save_dir(pkg, save, sizeof(save))) {
    fprintf(out, "\n== portable save folder %s\n", save);
    list_tree(out, save, 0);
    char* slash = strrchr(save, '/');
    if (slash) {
      *slash = 0;
      fprintf(out, "\n== game parent %s\n", save);
      list_tree(out, save, 0);
    }
  } else {
    fprintf(out, "\n[portable save folder unknown]\n");
  }
  fprintf(out, "\n== files dir %s\n", ext);
  list_tree(out, ext, 0);
  snprintf(path, sizeof(path), "%s/args.txt", ext);
  cat_file(out, "args.txt", path);
  fclose(out);
}

// ---- Crash capture into Download/GTAIV-Native: logcat of this process
// (apps may read their own log) and a native fault report.
static const char kDiagRoot[] = "/storage/emulated/0/Download/GTAIV-Native";
static int g_crash_fd = -1;
static struct sigaction g_prev[32];

static void put(int fd, const char* s) { if (fd >= 0) (void)!write(fd, s, strlen(s)); }
static void put_hex(int fd, uintptr_t v) {
  char b[19]; b[0] = '0'; b[1] = 'x';
  for (int i = 0; i < 16; ++i) b[2 + i] = "0123456789abcdef"[(v >> (60 - 4 * i)) & 15];
  b[18] = 0; put(fd, b);
}
static void put_frame(int fd, uintptr_t pc) {
  Dl_info info;
  put(fd, "  "); put_hex(fd, pc);
  if (dladdr((void*)pc, &info) && info.dli_fname) {
    put(fd, " "); put(fd, info.dli_fname); put(fd, "+"); put_hex(fd, pc - (uintptr_t)info.dli_fbase);
    if (info.dli_sname) { put(fd, " "); put(fd, info.dli_sname); }
  }
  put(fd, "\n");
}

static void on_fault(int sig, siginfo_t* si, void* uc_) {
  int fd = g_crash_fd;
  if (fd >= 0) {
    ucontext_t* uc = (ucontext_t*)uc_;
    put(fd, "\n=== native crash: signal ");
    char n[8]; snprintf(n, sizeof(n), "%d", sig); put(fd, n);
    put(fd, " fault addr "); put_hex(fd, (uintptr_t)si->si_addr);
    put(fd, "\npc:\n"); put_frame(fd, uc->uc_mcontext.pc);
    put(fd, "lr:\n"); put_frame(fd, uc->uc_mcontext.regs[30]);
    put(fd, "frames:\n");
    uintptr_t fp = uc->uc_mcontext.regs[29];
    for (int i = 0; i < 24 && fp && !(fp & 7); ++i) {
      uintptr_t* f = (uintptr_t*)fp;
      if (msync((void*)(fp & ~(uintptr_t)4095), 4096, MS_ASYNC) != 0) break;
      put_frame(fd, f[1]);
      if (f[0] <= fp) break;
      fp = f[0];
    }
    fsync(fd);
  }
  sigaction(sig, &g_prev[sig], NULL);  // chain to the previous handler / default
  raise(sig);
}

static void* logcat_thread(void* unused) {
  (void)unused;
  char cmd[200];
  snprintf(cmd, sizeof(cmd), "logcat -v threadtime --pid=%d", getpid());
  char path[300];
  snprintf(path, sizeof(path), "%s/logcat.txt", kDiagRoot);
  FILE* out = fopen(path, "w");
  FILE* in = popen(cmd, "r");
  if (!out || !in) { if (out) fclose(out); if (in) pclose(in); return NULL; }
  char line[1024];
  long total = 0;
  while (fgets(line, sizeof(line), in)) {
    fputs(line, out);
    fflush(out);
    if ((total += (long)strlen(line)) > 32L * 1024 * 1024) break;
  }
  pclose(in);
  fclose(out);
  return NULL;
}

// The log daemon keeps the previous run's lines (this app's UID may read its
// own entries, crash buffer included): dump them before this run adds more.
static void dump_previous_logcat(void) {
  char path[300], cmd[400];
  snprintf(path, sizeof(path), "%s/logcat-lastrun.txt", kDiagRoot);
  snprintf(cmd, sizeof(cmd), "logcat -d -b main,system,crash -v threadtime > '%s' 2>&1", path);
  int rc = system(cmd);
  __android_log_print(ANDROID_LOG_INFO, TAG, "logcat dump rc=%d", rc);
}

static void start_crash_capture(void) {
  mkdirs(kDiagRoot);
  dump_previous_logcat();
  char a[300], b[300];
  snprintf(a, sizeof(a), "%s/logcat.txt", kDiagRoot);
  snprintf(b, sizeof(b), "%s/logcat-previous.txt", kDiagRoot);
  rename(a, b);
  snprintf(a, sizeof(a), "%s/crash.txt", kDiagRoot);
  snprintf(b, sizeof(b), "%s/crash-previous.txt", kDiagRoot);
  rename(a, b);
  g_crash_fd = open(a, O_WRONLY | O_CREAT | O_TRUNC, 0644);
  put(g_crash_fd, "GTA IV Native run log (lanshim). Empty below = no native crash caught.\n");
  struct sigaction sa;
  memset(&sa, 0, sizeof(sa));
  sa.sa_sigaction = on_fault;
  sa.sa_flags = SA_SIGINFO | SA_ONSTACK;
  sigemptyset(&sa.sa_mask);
  const int sigs[] = {SIGSEGV, SIGBUS, SIGILL, SIGFPE, SIGABRT, SIGTRAP};
  for (unsigned i = 0; i < sizeof(sigs) / sizeof(sigs[0]); ++i) sigaction(sigs[i], &sa, &g_prev[sigs[i]]);
  pthread_t t;
  if (pthread_create(&t, NULL, logcat_thread, NULL) == 0) pthread_detach(t);
}

__attribute__((constructor)) static void lanshim_init(void) {
  {
    char pkg[128];
    if (package_name(pkg, sizeof(pkg))) {
      char args[300];
      snprintf(args, sizeof(args), "/storage/emulated/0/Android/data/%s/files/args.txt", pkg);
      struct stat ast;
      g_args_fresh = stat(args, &ast) == 0 && time(NULL) - ast.st_mtime < 90;
      write_diagnostics(pkg);  // first: shows the state left by the previous run
      start_crash_capture();
      ensure_render_fixes(pkg);
      reset_texture_cache_once(pkg);
    }
  }
  sync_touch_icons();
  install_complete_save();
  reset_foreign_touch_layout();
  pthread_t t;
  if (pthread_create(&t, NULL, take_multicast_lock, NULL) == 0) pthread_detach(t);
}
