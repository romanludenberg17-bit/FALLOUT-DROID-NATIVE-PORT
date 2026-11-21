// Fallout Droid NV Native — AArch64 patch code for libfnv.so
// Linked at FNVX_VADDR (0x58e0000): a NEW R+X PT_LOAD segment appended to
// libfnv.so by build_fnv_native.py (the unused PT_NOTE program header is
// converted). Since v1.3.1 the old code cave 0x53fb664.. is NOT used any more.
//
// External symbols are provided by the linker (--defsym), see build script.
// RW state lives in the .bss extension at STATE (see layout below).
//
// STATE layout:
//   +0x00 f32 att        limiter attenuation (1 - gain)
//   +0x04 i32 fifo       0 = untried, >0 = fd, -1 = off
//   +0x08 f32 peak       max |mix| (after master gain) since last stats line
//   +0x0c f32 attmax     max attenuation since last stats line
//   +0x10 u32 callbacks  AAudio data callbacks
//   +0x14 u32 creates    voices created (DirectSound Play)
//   +0x18 u32 fails      voice creation failures
//   +0x20 u32 reclaims   finished one-shot voices reclaimed on full pool
//   +0x24 u32 nplaylog   "[dsnd] play" lines printed
//   +0x28 u32 nfaillog   "[dsnd] FAIL" lines printed
//   +0x40 u32 owner[256] guest IDirectSoundBuffer that owns voice i

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
    ldr     w8, [x21, #0x10]
    add     w8, w8, #1
    str     w8, [x21, #0x10]            // callbacks++
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
    ldr     s25, [x21, #8]              // peak (stats)
    ldr     s26, [x21, #12]
    fsub    s26, s0, s26                // gmin (stats)
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
    fmaxnm  s25, s25, s2
    fmul    s3, s2, s22
    fcmp    s3, s17
    b.le    .Lrelease
    fdiv    s22, s17, s2                // attack: g = T / peak
    b       .Lapply
.Lrelease:
    fmadd   s22, s22, s18, s19          // g = g*rel + (1-rel)  -> 1.0
.Lapply:
    fminnm  s26, s26, s22
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
    str     s25, [x21, #8]
    fmov    s0, #1.0
    fsub    s0, s0, s26
    str     s0, [x21, #12]
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
// STUB 6: DirectSound voice creation wrapper.  Hooked at 0x5413d64
// (bl voice_create inside IDirectSoundBuffer8::Play).  Same args as
// voice_create(obj, data, bytes, channels, bits, rate, startpos, loop).
//  - records the owning buffer of every voice (owner[])
//  - if creation fails with a valid PCM format (pool of 256 voices full),
//    reclaims finished non-looping voices through the game's own status-sync
//    path (DSOUND_BUFFER_RESET(obj), the same as GetStatus) and retries
//  - logs voice creations / failures / stats to the game log (stderr)
// ---------------------------------------------------------------------------
    .globl stub_vcreate
stub_vcreate:
    stp     x29, x30, [sp, #-112]!
    mov     x29, sp
    stp     x19, x20, [sp, #16]
    stp     x21, x22, [sp, #32]
    stp     x23, x24, [sp, #48]
    stp     x25, x26, [sp, #64]
    stp     x27, x28, [sp, #80]
    mov     w19, w0                     // obj (guest address)
    mov     x20, x1                     // data (host)
    mov     w21, w2                     // bytes
    mov     w22, w3                     // channels
    mov     w23, w4                     // bits
    mov     w24, w5                     // rate
    mov     w25, w6                     // start position
    mov     w26, w7                     // loop
    bl      voice_create
    mov     w27, w0
    adrp    x28, STATE
    add     x28, x28, :lo12:STATE
    tbz     w27, #31, .Lv_ok

    ldr     w8, [x28, #0x18]
    add     w8, w8, #1
    str     w8, [x28, #0x18]            // fails++
    sub     w8, w22, #1
    cmp     w8, #1
    b.hi    .Lv_failed                  // channels not 1..2: format reject
    cmp     w23, #8
    ccmp    w23, #16, #4, ne
    b.ne    .Lv_failed                  // bits not 8/16: format reject

    str     wzr, [sp, #96]              // reclaimed = 0
    mov     x27, #0
.Lr_loop:
    adrp    x8, VOICES
    add     x8, x8, :lo12:VOICES
    mov     x9, #0x58
    madd    x8, x27, x9, x8
    ldr     w9, [x8]
    cmp     w9, #1
    b.ne    .Lr_next                    // not a buffer voice
    ldr     w9, [x8, #0x24]
    cbz     w9, .Lr_next                // still playing
    ldr     w9, [x8, #0x20]
    cbnz    w9, .Lr_next                // looping
    add     x9, x28, #0x40
    ldr     w0, [x9, x27, lsl #2]
    cbz     w0, .Lr_next                // unknown owner
    adrp    x9, GOT_GUESTMEM
    ldr     x9, [x9, :lo12:GOT_GUESTMEM]
    ldr     x9, [x9]
    add     w10, w0, #0x24
    ldr     w10, [x9, w10, uxtw]
    add     w11, w27, #1
    cmp     w10, w11
    b.ne    .Lr_next                    // owner no longer references this voice
    bl      DSOUND_BUFFER_RESET         // sync status: frees the finished voice
    ldr     w8, [sp, #96]
    add     w8, w8, #1
    str     w8, [sp, #96]
.Lr_next:
    add     x27, x27, #1
    cmp     x27, #256
    b.lo    .Lr_loop

    ldr     w8, [sp, #96]
    mov     w27, #-1
    cbz     w8, .Lv_failed
    ldr     w9, [x28, #0x20]
    add     w9, w9, w8
    str     w9, [x28, #0x20]            // reclaims += n
    mov     w0, w19
    mov     x1, x20
    mov     w2, w21
    mov     w3, w22
    mov     w4, w23
    mov     w5, w24
    mov     w6, w25
    mov     w7, w26
    bl      voice_create                // retry
    mov     w27, w0
    adrp    x8, GOT_STDERR
    ldr     x8, [x8, :lo12:GOT_STDERR]
    ldr     x0, [x8]
    adr     x1, str_reclaim
    ldr     w2, [sp, #96]
    mov     w3, w27
    bl      fprintf
    tbz     w27, #31, .Lv_ok

.Lv_failed:
    ldr     w8, [x28, #0x28]
    cmp     w8, #32
    b.hs    .Lv_ret
    add     w8, w8, #1
    str     w8, [x28, #0x28]
    adrp    x8, GOT_STDERR
    ldr     x8, [x8, :lo12:GOT_STDERR]
    ldr     x0, [x8]
    adr     x1, str_vfail
    mov     w2, w19
    mov     w3, w22
    mov     w4, w23
    mov     w5, w24
    mov     w6, w21
    bl      fprintf
    bl      print_stats
    b       .Lv_ret

.Lv_ok:
    add     x8, x28, #0x40
    str     w19, [x8, w27, uxtw #2]     // owner[v] = obj
    ldr     w8, [x28, #0x14]
    add     w8, w8, #1
    str     w8, [x28, #0x14]            // creates++
    ldr     w9, [x28, #0x24]
    cmp     w9, #64
    b.hs    .Lv_stats
    add     w9, w9, #1
    str     w9, [x28, #0x24]
    adrp    x8, GOT_STDERR
    ldr     x8, [x8, :lo12:GOT_STDERR]
    ldr     x0, [x8]
    adr     x1, str_vplay
    mov     w2, w19
    mov     w3, w27
    mov     w4, w22
    mov     w5, w23
    mov     w6, w24
    mov     w7, w21
    bl      fprintf
.Lv_stats:
    ldr     w8, [x28, #0x14]
    tst     w8, #0xff
    b.ne    .Lv_ret
    bl      print_stats                 // every 256 voice creations
.Lv_ret:
    mov     w0, w27
    ldp     x19, x20, [sp, #16]
    ldp     x21, x22, [sp, #32]
    ldp     x23, x24, [sp, #48]
    ldp     x25, x26, [sp, #64]
    ldp     x27, x28, [sp, #80]
    ldp     x29, x30, [sp], #112
    ret

// print_stats(): one "[dsnd] stats" line, resets peak / attmax.
print_stats:
    stp     x29, x30, [sp, #-16]!
    mov     x29, sp
    adrp    x8, VOICES
    add     x8, x8, :lo12:VOICES
    mov     w9, #0                      // active voices
    mov     w10, #0                     // finished-but-held voices
    mov     w11, #256
1:  ldr     w12, [x8]
    cbz     w12, 2f
    add     w9, w9, #1
    cmp     w12, #1
    b.ne    2f
    ldr     w12, [x8, #0x24]
    cbz     w12, 2f
    add     w10, w10, #1
2:  add     x8, x8, #0x58
    subs    w11, w11, #1
    b.ne    1b
    adrp    x11, STATE
    add     x11, x11, :lo12:STATE
    ldr     w2, [x11, #0x10]
    ldr     w3, [x11, #0x14]
    ldr     w4, [x11, #0x18]
    ldr     w5, [x11, #0x20]
    mov     w6, w9
    mov     w7, w10
    ldr     s0, [x11, #8]
    fcvt    d0, s0
    ldr     s1, [x11, #12]
    fmov    s2, #1.0
    fsub    s1, s2, s1
    fcvt    d1, s1
    str     wzr, [x11, #8]
    str     wzr, [x11, #12]
    adrp    x8, GOT_STDERR
    ldr     x8, [x8, :lo12:GOT_STDERR]
    ldr     x0, [x8]
    adr     x1, str_stats
    bl      fprintf
    ldp     x29, x30, [sp], #16
    ret


// ---------------------------------------------------------------------------
// STUB 7/8: audio-only HLE call trace.  FNV_TRACE=1 normally prints EVERY
// import call (unusably slow); these gates print only calls whose import
// entry (dll + function name, 0x80 bytes at x20) mentions
// "ound" / "raph" / "edia" / "udio"  (DirectSound*, IGraphBuilder,
// IMedia*, IBasicAudio, ...).
//   0x53e4a50  cbnz w8, pre-print   ->  bl trace_pre
//   0x53e4a60  cbnz w8, post-print  ->  bl trace_post
// Context: x19 = guest CPU ctx, x20 = import entry, w8 = FNV_TRACE flag.
// ---------------------------------------------------------------------------
trace_match:                            // -> w0 = 1 if audio-related
    movz    w12, #0x756f
    movk    w12, #0x646e, lsl #16       // "ound"
    movz    w13, #0x6172
    movk    w13, #0x6870, lsl #16       // "raph"
    movz    w14, #0x6465
    movk    w14, #0x6169, lsl #16       // "edia"
    movz    w15, #0x6475
    movk    w15, #0x6f69, lsl #16       // "udio"
    mov     x9, #0
1:  ldr     w10, [x20, x9]
    cmp     w10, w12
    ccmp    w10, w13, #4, ne
    ccmp    w10, w14, #4, ne
    ccmp    w10, w15, #4, ne
    b.eq    2f
    add     x9, x9, #1
    cmp     x9, #0x7c
    b.lo    1b
    mov     w0, #0
    ret
2:  mov     w0, #1
    ret

    .globl trace_pre
trace_pre:
    cbz     w8, 9f
    stp     x29, x30, [sp, #-16]!
    mov     x29, sp
    bl      trace_match
    cbz     w0, 8f
    adrp    x8, GOT_STDERR
    ldr     x8, [x8, :lo12:GOT_STDERR]
    ldr     x0, [x8]
    adrp    x10, GUEST_BASE
    ldr     x10, [x10, :lo12:GUEST_BASE]
    ldr     w9, [x19, #0x10]            // guest esp
    add     w11, w9, #4
    ldr     w4, [x10, w11, uxtw]
    add     w11, w9, #8
    ldr     w5, [x10, w11, uxtw]
    add     w11, w9, #12
    ldr     w6, [x10, w11, uxtw]
    add     w11, w9, #16
    ldr     w7, [x10, w11, uxtw]
    mov     x2, x20
    add     x3, x20, #0x40
    adrp    x1, FMT_IMP
    add     x1, x1, :lo12:FMT_IMP
    bl      fprintf
8:  ldp     x29, x30, [sp], #16
9:  ret

    .globl trace_post
trace_post:
    cbz     w8, 9f
    stp     x29, x30, [sp, #-16]!
    mov     x29, sp
    bl      trace_match
    cbz     w0, 8f
    adrp    x8, GOT_STDERR
    ldr     x8, [x8, :lo12:GOT_STDERR]
    ldr     x0, [x8]
    ldr     w2, [x19]
    adrp    x1, FMT_IMP_RET
    add     x1, x1, :lo12:FMT_IMP_RET
    bl      fprintf
8:  ldp     x29, x30, [sp], #16
9:  ret

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
str_vplay:        .asciz "[dsnd] play buf=%#x voice=%d ch=%d bits=%d hz=%d bytes=%u\n"
str_vfail:        .asciz "[dsnd] FAIL voice for buf=%#x ch=%d bits=%d hz=%d bytes=%u\n"
str_reclaim:      .asciz "[dsnd] voice pool full: reclaimed %d finished voices, retry -> %d\n"
str_stats:        .asciz "[dsnd] stats: callbacks=%u creates=%u fails=%u reclaims=%u active=%d finished_held=%d peak=%.3f min_gain=%.3f\n"
str_aaudio_log:   .asciz "[audio] AAudio+lim %d Hz, burst %d, buffer %d\n"
    .globl fnvx_end
fnvx_end:
