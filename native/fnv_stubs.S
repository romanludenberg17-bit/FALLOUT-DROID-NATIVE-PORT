// Fallout Droid NV Native — AArch64 patch stubs for libfnv.so
// Linked at CAVE_START (0x53fb664). Hard limit: CAVE_END (0x53fb9a0) —
// 0x53fb9a0..0x53fb9c8 is LIVE code (__stack_chk_fail + FNV_QUIT_AFTER exit),
// 0x53fb9ec is a live function. build_fnv_native.py asserts both.
//
// External symbols are provided by the linker (--defsym), see build script.

    .text
    .p2align 2

// ---------------------------------------------------------------------------
// STUB 1: audio post-mix.  Hooked at 0x54130e0 (`mov w0, wzr` before the
// epilogue of the AAudio data callback).  On entry: x19 = float* interleaved
// stereo buffer, w20 = numFrames.  Caller restores its callee-saved regs
// (x19-x28, d8-d10) itself; we still only use caller-saved SIMD regs and
// save x21/x22.  Must return w0 = 0 (AAUDIO_CALLBACK_RESULT_CONTINUE).
//
// The original hard [-1,1] clamp of the mixer is disabled (0x5413040 -> b),
// so here we see the true mix and apply:
//   1. NaN/Inf-safe input (NaN, +-Inf -> 0)
//   2. master gain MASTER_GAIN
//   3. stereo-linked peak limiter: instant attack, ~80 ms release,
//      threshold LIMIT_T  -> no clipping, transparent below threshold
//   4. final safety clamp [-1, 1]
// Output goes back to AAudio as float (default), or — if FNV_PCM_FIFO is set
// and the Java AudioTrack reader is connected — as PCM16 into the FIFO
// (AAudio buffer is then zeroed).  EPIPE on the FIFO falls back to AAudio.
//
// State lives in an extension of .bss (LOAD[2].p_memsz grown by the build):
//   [STATE+0] float  att  (1 - limiter gain), 0 at start
//   [STATE+4] int32  fifo: 0 = not tried / reader not ready, >0 = fd, -1 = off
// ---------------------------------------------------------------------------
    .globl stub_audio
stub_audio:
    stp     x29, x30, [sp, #-48]!
    mov     x29, sp
    stp     x21, x22, [sp, #16]
    adrp    x21, STATE
    add     x21, x21, :lo12:STATE

    ldr     w22, [x21, #4]
    cbnz    w22, .Lfifo_known
    adr     x0, str_fifo_env
    bl      getenv
    cbnz    x0, .Lfifo_open
    mov     w22, #-1                    // no FIFO requested: AAudio only
    str     w22, [x21, #4]
    b       .Lfifo_known
.Lfifo_open:
    mov     w1, #0x0801                 // O_WRONLY | O_NONBLOCK
    movk    w1, #0x8, lsl #16           // | O_CLOEXEC
    bl      open
    cmp     w0, #0
    b.le    .Lfifo_known                // reader not ready yet (ENXIO): retry later
    mov     w22, w0
    str     w22, [x21, #4]
    adrp    x8, GOT_STDERR
    ldr     x8, [x8, :lo12:GOT_STDERR]
    ldr     x0, [x8]
    adr     x1, str_fifo_log
    mov     w2, w22
    bl      fprintf

.Lfifo_known:
    cmp     w20, #1
    b.lt    .Ldone
    cmp     w22, #0
    cset    w15, gt                     // w15 = PCM16-to-FIFO mode

    ldr     s16, c_master
    ldr     s17, c_limit
    ldr     s18, c_rel
    ldr     s19, c_1mrel
    ldr     s23, c_s16
    fmov    v20.2s, #1.0
    fmov    v21.2s, #-1.0
    ldr     s24, c_big
    dup     v24.2s, v24.s[0]
    ldr     s22, [x21]                  // att
    fmov    s0, #1.0
    fsub    s22, s0, s22                // g = 1 - att
    mov     x10, x19                    // float read ptr
    mov     x12, x19                    // pcm16 write ptr (always behind reads)
    mov     w11, w20

.Lloop:
    ldr     d0, [x10], #8               // v0.2s = {L, R}
    facge   v1.2s, v24.2s, v0.2s        // |x| <= 1e30 ? (false for NaN/Inf)
    and     v0.8b, v0.8b, v1.8b         // NaN / Inf lanes -> 0
    fmul    v0.2s, v0.2s, v16.s[0]      // master gain
    fabs    v2.2s, v0.2s
    fmaxnmp s2, v2.2s                   // stereo-linked peak
    fmul    s3, s2, s22
    fcmp    s3, s17
    b.le    .Lrelease
    fdiv    s22, s17, s2                // attack: g = T / peak
    b       .Lapply
.Lrelease:
    fmadd   s22, s22, s18, s19          // g = g*rel + (1-rel)  -> 1.0
.Lapply:
    fmul    v0.2s, v0.2s, v22.s[0]
    fminnm  v0.2s, v0.2s, v20.2s
    fmaxnm  v0.2s, v0.2s, v21.2s
    cbnz    w15, .Lpcm16
    str     d0, [x10, #-8]
    b       .Lnext
.Lpcm16:
    fmul    v0.2s, v0.2s, v23.s[0]
    fcvtns  v0.2s, v0.2s
    sqxtn   v0.4h, v0.4s
    str     s0, [x12], #4
.Lnext:
    subs    w11, w11, #1
    b.ne    .Lloop

    fmov    s0, #1.0
    fsub    s0, s0, s22
    str     s0, [x21]                   // save att
    cbz     w15, .Ldone

    // Block SIGPIPE on this (audio) thread so a dead reader yields EPIPE
    // instead of killing the game process.
    mov     x8, #0x1000                 // 1 << (SIGPIPE - 1)
    str     x8, [sp, #32]
    mov     x0, #0                      // SIG_BLOCK
    add     x1, sp, #32
    mov     x2, #0
    mov     x3, #8
    mov     x8, #135                    // __NR_rt_sigprocmask
    svc     #0

    mov     w0, w22
    mov     x1, x19
    lsl     w2, w20, #2
    mov     x8, #64                     // __NR_write (non-blocking; EAGAIN = drop)
    svc     #0
    cmn     x0, #32                     // -EPIPE: reader gone
    b.ne    .Lsilence
    mov     w0, w22
    mov     x8, #57                     // __NR_close
    svc     #0
    mov     w8, #-1                     // fall back to AAudio from next callback
    str     w8, [x21, #4]
.Lsilence:
    mov     x0, x19
    mov     w1, #0
    ubfiz   x2, x20, #3, #32
    bl      memset

.Ldone:
    mov     w0, #0
    ldp     x21, x22, [sp, #16]
    ldp     x29, x30, [sp], #48
    ret

// ---------------------------------------------------------------------------
// STUB 5: AAudio builder tuning.  Hooked at 0x5412b88 (bl setPerformanceMode).
// Calls the original, then (if available) setBufferCapacityInFrames(4096)
// (~85 ms @ 48 kHz) so the stream has real headroom against callback jitter.
// setBufferSizeInFrames(3*burst) at 0x5412c10 is patched to request 65536,
// which AAudio clamps to the capacity.
// ---------------------------------------------------------------------------
    .globl stub_builder
stub_builder:
    stp     x29, x30, [sp, #-32]!
    mov     x29, sp
    str     x19, [sp, #16]
    mov     x19, x0
    bl      AAudioStreamBuilder_setPerformanceMode
    mov     x0, #0                      // RTLD_DEFAULT
    adr     x1, str_set_capacity
    bl      dlsym
    cbz     x0, 1f
    mov     x8, x0
    mov     x0, x19
    mov     w1, #4096
    blr     x8
1:  ldr     x19, [sp, #16]
    ldp     x29, x30, [sp], #32
    ret

// ---------------------------------------------------------------------------
// STUB 2: IDirectSound8::GetCaps — real DSCAPS (from v1.2.15). Hooked at
// 0x54134d0 (bl memset; x0 = &caps.dwFlags, x1 = 0, x2 = dwSize - 4).
// ---------------------------------------------------------------------------
    .globl stub_dscaps
stub_dscaps:
    stp     x29, x30, [sp, #-32]!
    str     x0, [sp, #16]
    bl      memset
    ldr     x8, [sp, #16]
    mov     w9, #0x0fff
    str     w9, [x8, #0]                // dwFlags
    mov     w9, #100
    str     w9, [x8, #4]                // dwMinSecondarySampleRate
    mov     w9, #0x0d40
    movk    w9, #0x3, lsl #16
    str     w9, [x8, #8]                // dwMaxSecondarySampleRate = 200000
    mov     w9, #1
    str     w9, [x8, #12]               // dwPrimaryBuffers
    mov     w9, #64
    str     w9, [x8, #16]
    str     w9, [x8, #20]
    str     w9, [x8, #24]
    str     w9, [x8, #28]
    str     w9, [x8, #32]
    str     w9, [x8, #36]
    str     w9, [x8, #40]
    str     w9, [x8, #52]
    ldp     x29, x30, [sp], #32
    ret

// ---------------------------------------------------------------------------
// STUB 3: IDirectSoundBuffer8::Play one-shot replay sync (from v1.2.15).
// Hooked at 0x5413cc8 (ldr x8, [x21]).
// ---------------------------------------------------------------------------
    .globl stub_play
stub_play:
    stp     x29, x30, [sp, #-32]!
    str     x7, [sp, #16]
    mov     w0, w20
    bl      DSOUND_BUFFER_RESET
    ldr     x7, [sp, #16]
    ldr     x8, [x21]
    ldp     x29, x30, [sp], #32
    ret

// ---------------------------------------------------------------------------
// STUB 4: dsound_update_volume 3D pan NaN clamp (from v1.2.15).
// Hooked at 0x5414434 (fmov s0, #1.0).  s5 = pan -> clamp to [-1, 1].
// ---------------------------------------------------------------------------
    .globl stub_pan
stub_pan:
    fmov    s0, #1.0
    fmov    s2, #-1.0
    fmaxnm  s5, s5, s2
    fminnm  s5, s5, s0
    ret

// ---------------------------------------------------------------------------
// Constants & strings (inside the cave, reached with adr / ldr literal)
// ---------------------------------------------------------------------------
    .p2align 2
c_master:   .float 0.70794578           // -3 dB master headroom
c_limit:    .float 0.94406087           // limiter threshold -0.5 dBFS
c_rel:      .float 0.99973965           // exp(-1 / (0.080 s * 48000))
c_1mrel:    .float 0.00026035
c_s16:      .float 32767.0
c_big:      .float 1.0e30
str_fifo_env:     .asciz "FNV_PCM_FIFO"
str_fifo_log:     .asciz "[audio] PCM FIFO fd=%d\n"
str_set_capacity: .asciz "AAudioStreamBuilder_setBufferCapacityInFrames"
    .globl str_aaudio_log
str_aaudio_log:   .asciz "[audio] AAudio+lim %d Hz, burst %d, buffer %d\n"
    .globl cave_end
cave_end:
