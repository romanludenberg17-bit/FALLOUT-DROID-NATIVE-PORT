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
    if (access(bak, F_OK) != 0) rename(to, bak);
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

// ---- Portable save layout used by the engine (one folder per save):
//   <save>/<XUID>/545407F2/00000001/<NAME>/<NAME>           raw SAVEBLOCK data
//   <save>/<XUID>/545407F2/00000001/<NAME>/__thumbnail.png
//   <save>/<XUID>/545407F2/Headers/00000001/<NAME>.header   XCONTENT_AGGREGATE_DATA (0x148)
static int is_hex_id(const char* n) {
  if (strlen(n) != 16) return 0;
  for (const char* c = n; *c; ++c) if (!isxdigit((unsigned char)*c)) return 0;
  return 1;
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

static int g_args_fresh;

// 1.2.2-1.3.2 offered a bundled "100% complete" save: take it out again.
// Its SGTA400 slots (one per profile) go to <save>-quarantine, once.
static void remove_complete_save_once(const char* portable_dir) {
  char marker[460];
  snprintf(marker, sizeof(marker), "%s/.no-complete-save-v633", portable_dir);
  if (access(marker, F_OK) == 0) return;
  DIR* d = opendir(portable_dir);
  if (!d) return;
  struct dirent* e;
  while ((e = readdir(d))) {
    if (!is_hex_id(e->d_name)) continue;
    static const char* files[] = {"00000001/SGTA400/SGTA400", "00000001/SGTA400/SGTA400.bak",
                                  "00000001/SGTA400/__thumbnail.png", "Headers/00000001/SGTA400.header",
                                  "00000001/SGTA400"};
    for (unsigned i = 0; i < sizeof(files) / sizeof(files[0]); ++i) {
      char p[700];
      snprintf(p, sizeof(p), "%s/%s/545407F2/%s", portable_dir, e->d_name, files[i]);
      struct stat st;
      if (stat(p, &st) != 0) continue;
      if (S_ISDIR(st.st_mode)) rmdir(p);
      else quarantine(portable_dir, p, e->d_name);
    }
  }
  closedir(d);
  FILE* m = fopen(marker, "w");
  if (m) fclose(m);
}

// args.txt repair, every start:
// - co-op starts append "--gta4_multiplayer_backend=lan" + player name and
//   never remove them, so every later start booted in network mode and hung
//   on a black screen: keep them only for the start that just wrote them;
// - lines older builds of this APK added (100% save requests, render flags).
static void repair_args(const char* pkg) {
  char args[300];
  snprintf(args, sizeof(args), "/storage/emulated/0/Android/data/%s/files/args.txt", pkg);
  FILE* f = fopen(args, "r");
  if (!f) return;
  char* kept = NULL;
  size_t kept_len = 0, cap = 0;
  int stripped = 0;
  char line[512];
  while (fgets(line, sizeof(line), f)) {
    static const char* always[] = {"--gta4_complete_save=", "--gta4_restore_save=",
                                   "--gta4_native_mobile_coverage_free=false", "--gta4_native_mobile_opaque_depth=false",
                                   "# Vehicle badges / decals drew a black box", "--gta4_skip_intro=true",
                                   "--gta4_native_ubo_shared_constants=false", "--gta4_native_gpu_detile=false",
                                   "--gta4_native_drop_inert_blending=false",
                                   "# Transparent decals / mission markers (render fixes)"};
    static const char* network[] = {"--gta4_multiplayer_backend=", "--gta4_player_name=", "--gta4_lan_ipv4=",
                                    "#-gta4_skip_intro=true", "# LAN co-op (serverless)"};
    int drop = 0;
    for (unsigned i = 0; i < sizeof(always) / sizeof(always[0]) && !drop; ++i)
      drop = !strncmp(line, always[i], strlen(always[i]));
    for (unsigned i = 0; i < sizeof(network) / sizeof(network[0]) && !drop && !g_args_fresh; ++i)
      drop = !strncmp(line, network[i], strlen(network[i]));
    if (drop) { stripped = 1; continue; }
    size_t l = strlen(line);
    if (kept_len + l + 1 > cap) {
      cap = (cap + l + 1) * 2;
      char* grown = realloc(kept, cap);
      if (!grown) { free(kept); fclose(f); return; }
      kept = grown;
    }
    memcpy(kept + kept_len, line, l); kept_len += l; kept[kept_len] = 0;
  }
  fclose(f);
  if (stripped) {
    f = fopen(args, "w");
    if (f) { if (kept) fwrite(kept, 1, kept_len, f); fclose(f); }
  }
  free(kept);
  __android_log_print(ANDROID_LOG_INFO, TAG, "args: network mode %s", g_args_fresh ? "kept (co-op start)" : "off");
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

// 1.2.5-1.3.1 moved <game parent>/shader_cache/texture_cache aside once
// ("reset"). It is not a disposable cache: it holds the textures the installer
// converted from game.iso, and without it the game stays on a black screen.
// Put it back.
static void restore_texture_cache(const char* pkg) {
  char save[400];
  if (!portable_save_dir(pkg, save, sizeof(save))) return;
  char* slash = strrchr(save, '/');
  if (!slash) return;
  *slash = 0;  // <game parent>
  char cache[460], old[480], aside[500];
  snprintf(cache, sizeof(cache), "%s/shader_cache/texture_cache", save);
  snprintf(old, sizeof(old), "%s/shader_cache/texture_cache.old", save);
  if (access(old, F_OK) != 0) return;
  if (access(cache, F_OK) == 0) {
    // an incomplete cache the engine started after the move: keep it aside
    snprintf(aside, sizeof(aside), "%s/shader_cache/texture_cache.partial", save);
    rename(cache, aside);
  }
  int ok = rename(old, cache) == 0;
  __android_log_print(ANDROID_LOG_INFO, TAG, "texture cache restored: %s", ok ? "ok" : strerror(errno));
}

// ---- Diagnostics in shared storage (Android/data is hidden from file
// managers on Android 11+): Download/GTAIV-Native/diag.txt lists args.txt,
// the selected game folder and the save folder, so it can be sent without adb.
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

// ---- Play time: <files>/playtime.txt holds the total seconds played. Every
// 30 s the tick counts if this process used real CPU time (a running game
// uses seconds of CPU per tick; paused in the background it uses almost none).
static char g_playtime_path[300];

static double process_cpu_seconds(void) {
  FILE* f = fopen("/proc/self/stat", "r");
  if (!f) return -1;
  char buf[1024];
  size_t n = fread(buf, 1, sizeof(buf) - 1, f);
  fclose(f);
  buf[n] = 0;
  char* p = strrchr(buf, ')');  // comm may contain spaces
  if (!p) return -1;
  unsigned long utime = 0, stime = 0;
  // fields after ")": state(3) ... utime(14) stime(15)
  if (sscanf(p + 2, "%*c %*d %*d %*d %*d %*d %*u %*u %*u %*u %*u %lu %lu", &utime, &stime) != 2) return -1;
  long hz = sysconf(_SC_CLK_TCK);
  return (double)(utime + stime) / (double)(hz > 0 ? hz : 100);
}

static void* playtime_thread(void* unused) {
  (void)unused;
  const int tick = 30;
  double last = process_cpu_seconds();
  for (;;) {
    sleep(tick);
    double now = process_cpu_seconds();
    int active = now >= 0 && last >= 0 && now - last > 3.0;
    last = now;
    if (!active) continue;
    long long total = 0;
    FILE* f = fopen(g_playtime_path, "r");
    if (f) { if (fscanf(f, "%lld", &total) != 1) total = 0; fclose(f); }
    total += tick;
    char tmp[320];
    snprintf(tmp, sizeof(tmp), "%s.tmp", g_playtime_path);
    f = fopen(tmp, "w");
    if (!f) continue;
    fprintf(f, "%lld\n", total);
    if (fclose(f) == 0) rename(tmp, g_playtime_path);
  }
  return NULL;
}

// ---- Mesa (Turnip) tuning. Android gives Mesa no cache directory (no HOME /
// XDG_CACHE_HOME), so Turnip recompiled every shader on every start: give it
// one in the app's private cache, 1 GiB. The vendor driver ignores these.
static void mesa_tuning(const char* pkg) {
  char dir[300];
  snprintf(dir, sizeof(dir), "/data/user/0/%s/cache/mesa", pkg);
  if (access("/data/user/0", F_OK) != 0) snprintf(dir, sizeof(dir), "/data/data/%s/cache/mesa", pkg);
  mkdirs(dir);
  setenv("MESA_SHADER_CACHE_DIR", dir, 0);
  setenv("MESA_SHADER_CACHE_MAX_SIZE", "1G", 0);
}

__attribute__((constructor)) static void lanshim_init(void) {
  char pkg[128];
  if (package_name(pkg, sizeof(pkg))) {
    char args[300];
    snprintf(args, sizeof(args), "/storage/emulated/0/Android/data/%s/files/args.txt", pkg);
    struct stat ast;
    g_args_fresh = stat(args, &ast) == 0 && time(NULL) - ast.st_mtime < 90;
    mesa_tuning(pkg);  // before the engine creates its Vulkan instance
    write_diagnostics(pkg);  // state left by the previous run
    restore_texture_cache(pkg);
    repair_args(pkg);
    char save[400];
    if (portable_save_dir(pkg, save, sizeof(save))) remove_complete_save_once(save);
    snprintf(g_playtime_path, sizeof(g_playtime_path), "/storage/emulated/0/Android/data/%s/files/playtime.txt", pkg);
    pthread_t pt;
    if (pthread_create(&pt, NULL, playtime_thread, NULL) == 0) pthread_detach(pt);
  }
  sync_touch_icons();
  reset_foreign_touch_layout();
  pthread_t t;
  if (pthread_create(&t, NULL, take_multicast_lock, NULL) == 0) pthread_detach(t);
}
