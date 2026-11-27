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

__attribute__((constructor)) static void lanshim_init(void) {
  reset_foreign_touch_layout();
  pthread_t t;
  if (pthread_create(&t, NULL, take_multicast_lock, NULL) == 0) pthread_detach(t);
}
