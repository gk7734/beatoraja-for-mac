// Metal renderer behind bms.player.beatoraja.metal.MetalRenderer (Java FFM).
// Draws SpriteBatch quads into a CAMetalLayer placed over the GLFW content view, or into render target
// textures standing in for GL framebuffers. GL keeps running underneath only for libGDX bookkeeping.
#import <Cocoa/Cocoa.h>
#import <Metal/Metal.h>
#import <QuartzCore/CAMetalLayer.h>
#include <dispatch/dispatch.h>

#define FRAMES 3
#define VERTEX_BYTES (32 * 1024 * 1024)
#define MAX_QUADS 8191
#define STRIP_ROWS 64

static NSString *const kShaders = @
"#include <metal_stdlib>\n"
"using namespace metal;\n"
"struct VIn { float2 pos [[attribute(0)]]; float4 color [[attribute(1)]]; float2 uv [[attribute(2)]]; };\n"
"struct VOut { float4 pos [[position]]; float4 color; float2 uv; };\n"
"struct DF { float outlineDistance; float shadowSmoothing; float2 shadowOffset; float4 outlineColor; float4 shadowColor; };\n"
// GL clip space has z in [-1, 1]; sprites are flat, so z is pinned inside Metal's [0, 1].
"vertex VOut v_custom(VIn in [[stage_in]], constant float4x4 &proj [[buffer(1)]]) {\n"
"  VOut o; o.pos = proj * float4(in.pos, 0.0, 1.0); o.pos.z = 0.0; o.color = in.color; o.uv = in.uv; return o; }\n"
// libGDX's default SpriteBatch shader scales alpha by 255/254 to undo its packed-colour rounding.
"vertex VOut v_default(VIn in [[stage_in]], constant float4x4 &proj [[buffer(1)]]) {\n"
"  VOut o; o.pos = proj * float4(in.pos, 0.0, 1.0); o.pos.z = 0.0; o.color = in.color; o.color.a *= 255.0 / 254.0; o.uv = in.uv; return o; }\n"
"fragment float4 f_default(VOut in [[stage_in]], texture2d<float> t [[texture(0)]], sampler s [[sampler(0)]]) {\n"
"  return in.color * t.sample(s, in.uv); }\n"
// glsl/layer.frag
"fragment float4 f_layer(VOut in [[stage_in]], texture2d<float> t [[texture(0)]], sampler s [[sampler(0)]]) {\n"
"  float4 c = t.sample(s, in.uv); if (c.r == 0.0 && c.g == 0.0 && c.b == 0.0) c.a = 0.0; return in.color * c; }\n"
// glsl/ffmpeg.frag
"fragment float4 f_ffmpeg(VOut in [[stage_in]], texture2d<float> t [[texture(0)]], sampler s [[sampler(0)]]) {\n"
"  float4 c = t.sample(s, in.uv); return in.color * float4(c.b, c.g, c.r, 1.0); }\n"
// glsl/bilinear.frag
"fragment float4 f_bilinear(VOut in [[stage_in]], texture2d<float> t [[texture(0)]], sampler s [[sampler(0)]]) {\n"
"  float2 ts = float2(t.get_width(), t.get_height()); float ca = t.sample(s, in.uv).a;\n"
"  if (ca <= 0.0) return float4(0.0);\n"
"  float tx = 0.5 / ts.x, ty = 0.5 / ts.y;\n"
"  float4 p00 = t.sample(s, in.uv + float2(-tx, -ty)), p10 = t.sample(s, in.uv + float2(tx, -ty));\n"
"  float4 p01 = t.sample(s, in.uv + float2(-tx, ty)), p11 = t.sample(s, in.uv + float2(tx, ty));\n"
"  float a = fract(in.uv.x * ts.x + 0.5), b = fract(in.uv.y * ts.y + 0.5);\n"
"  float4 q0 = mix(p00 * p00.a, p10 * p10.a, a), q1 = mix(p01 * p01.a, p11 * p11.a, a);\n"
"  float4 r = mix(q0, q1, b) / ca; r.a = ca; return in.color * r; }\n"
// glsl/distance_field.frag
"fragment float4 f_distance_field(VOut in [[stage_in]], texture2d<float> t [[texture(0)]], sampler s [[sampler(0)]], constant DF &u [[buffer(0)]]) {\n"
"  const float sm = 1.0 / 16.0; float d = t.sample(s, in.uv).a;\n"
"  float of = smoothstep(0.5 - sm, 0.5 + sm, d); float4 color = mix(u.outlineColor, in.color, of);\n"
"  float alpha = smoothstep(u.outlineDistance - sm, u.outlineDistance + sm, d);\n"
"  float4 mainColor = float4(color.rgb, color.a * alpha);\n"
"  float sd = t.sample(s, in.uv - u.shadowOffset).a;\n"
"  float sa = smoothstep(0.5 - u.shadowSmoothing, 0.5 + u.shadowSmoothing, sd);\n"
"  float4 shadow = float4(u.shadowColor.rgb, u.shadowColor.a * sa);\n"
"  return mix(shadow, mainColor, mainColor.a); }\n"
// Full-screen triangle that writes alpha 1 (the pipeline masks colour), so the window never shows through.
"vertex float4 v_fill(uint id [[vertex_id]]) {\n"
"  float2 p = float2((id << 1) & 2, id & 2); return float4(p * 2.0 - 1.0, 0.0, 1.0); }\n"
"fragment float4 f_opaque() { return float4(0.0, 0.0, 0.0, 1.0); }\n";

// Shader ids shared with MetalRenderer.java
enum { SHADER_DEFAULT = 0, SHADER_BILINEAR = 1, SHADER_FFMPEG = 2, SHADER_LAYER = 3, SHADER_DISTANCE_FIELD = 4, SHADER_COUNT };
// Texture kinds shared with MetalRenderer.java
enum { KIND_RGBA = 0, KIND_ALPHA = 1, KIND_LUMINANCE_ALPHA = 2, KIND_LUMINANCE = 3, KIND_TARGET = 4 };

/**
 * Transparent to the mouse so GLFW's content view keeps receiving every event. Opaque to AppKit: it re-applies the
 * view's opacity to the layer (e.g. when the window changes display), and a non-opaque layer lets the desktop show
 * through wherever a skin leaves alpha below 1.
 */
@interface BeatorajaMetalView : NSView
@end
@implementation BeatorajaMetalView
- (BOOL)wantsUpdateLayer { return YES; }
- (BOOL)isOpaque { return YES; }
- (CALayer *)makeBackingLayer { return [CAMetalLayer layer]; }
- (NSView *)hitTest:(NSPoint)point { return nil; }
- (BOOL)acceptsFirstResponder { return NO; }
@end

static id<MTLDevice> device;
static id<MTLCommandQueue> queue;
static id<MTLLibrary> library;
static id<MTLRenderPipelineState> opaquePipeline;
static CAMetalLayer *layer;
static NSMutableDictionary<NSNumber *, id<MTLRenderPipelineState>> *pipelines;
static NSMutableDictionary<NSNumber *, id<MTLSamplerState>> *samplers;
static id<MTLBuffer> vertexBuffers[FRAMES];
static id<MTLBuffer> indices;
static dispatch_semaphore_t inflight;
static int frameSlot;
static size_t vertexOffset;

static id<CAMetalDrawable> drawable;
static id<MTLCommandBuffer> commands;
static id<MTLRenderCommandEncoder> encoder;
/** Current render target: nil = drawable */
static id<MTLTexture> target;
static BOOL pendingClear;
static MTLClearColor clearColor;
static int drawableWidth, drawableHeight;

static id<MTLRenderPipelineState> pipeline(int shader, int blend, int src, int dst, int srcAlpha, int dstAlpha);

int bm_init(void *nswindow, int vsync, int nearest) {
    __block int ok = 0;
    void (^setup)(void) = ^{
        device = MTLCreateSystemDefaultDevice();
        if (!device) return;
        queue = [device newCommandQueue];
        NSError *error = nil;
        library = [device newLibraryWithSource:kShaders options:nil error:&error];
        if (!library) { NSLog(@"[metal] shader compile failed: %@", error); return; }
        pipelines = [NSMutableDictionary dictionary];
        MTLRenderPipelineDescriptor *fill = [MTLRenderPipelineDescriptor new];
        fill.vertexFunction = [library newFunctionWithName:@"v_fill"];
        fill.fragmentFunction = [library newFunctionWithName:@"f_opaque"];
        fill.colorAttachments[0].pixelFormat = MTLPixelFormatBGRA8Unorm;
        fill.colorAttachments[0].writeMask = MTLColorWriteMaskAlpha;
        opaquePipeline = [device newRenderPipelineStateWithDescriptor:fill error:&error];
        if (!opaquePipeline) NSLog(@"[metal] opaque pipeline failed: %@", error);
        samplers = [NSMutableDictionary dictionary];
        for (int i = 0; i < FRAMES; i++) {
            vertexBuffers[i] = [device newBufferWithLength:VERTEX_BYTES options:MTLResourceStorageModeShared | MTLResourceCPUCacheModeWriteCombined];
        }
        uint16_t *quads = malloc(MAX_QUADS * 6 * sizeof(uint16_t));
        for (int q = 0, v = 0; q < MAX_QUADS; q++, v += 4) {
            quads[q * 6 + 0] = v; quads[q * 6 + 1] = v + 1; quads[q * 6 + 2] = v + 2;
            quads[q * 6 + 3] = v + 2; quads[q * 6 + 4] = v + 3; quads[q * 6 + 5] = v;
        }
        indices = [device newBufferWithBytes:quads length:MAX_QUADS * 6 * sizeof(uint16_t) options:MTLResourceStorageModeShared];
        free(quads);
        inflight = dispatch_semaphore_create(FRAMES);

        NSWindow *window = (__bridge NSWindow *)nswindow;
        NSView *content = window.contentView;
        BeatorajaMetalView *view = [[BeatorajaMetalView alloc] initWithFrame:content.bounds];
        view.wantsLayer = YES;
        view.autoresizingMask = NSViewWidthSizable | NSViewHeightSizable;
        [content addSubview:view];
        layer = (CAMetalLayer *)view.layer;
        layer.device = device;
        layer.pixelFormat = MTLPixelFormatBGRA8Unorm;
        // The window's sRGB tag (MacWindowColorSpace) does not reach this layer; untagged, its pixels go to a
        // wide-gamut panel unconverted and look oversaturated.
        CGColorSpaceRef srgb = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
        layer.colorspace = srgb;
        CGColorSpaceRelease(srgb);
        // Screenshots read the drawable back.
        layer.framebufferOnly = NO;
        layer.opaque = YES;
        layer.displaySyncEnabled = vsync ? YES : NO;
        layer.magnificationFilter = nearest ? kCAFilterNearest : kCAFilterLinear;
        // Compile the usual pipelines now rather than stalling the first frame that needs them:
        // alpha blending, additive, and no blending, for every shader.
        static const int blends[][3] = { { 1, 0x302, 0x303 }, { 1, 0x302, 1 }, { 0, 0x302, 0x303 } };
        for (int shader = 0; shader < SHADER_COUNT; shader++) {
            for (int b = 0; b < 3; b++) pipeline(shader, blends[b][0], blends[b][1], blends[b][2], blends[b][1], blends[b][2]);
        }
        ok = 1;
    };
    if ([NSThread isMainThread]) setup(); else dispatch_sync(dispatch_get_main_queue(), setup);
    return ok;
}

static MTLBlendFactor blendFactor(int gl) {
    switch (gl) {
        case 0: return MTLBlendFactorZero;
        case 1: return MTLBlendFactorOne;
        case 0x300: return MTLBlendFactorSourceColor;
        case 0x301: return MTLBlendFactorOneMinusSourceColor;
        case 0x302: return MTLBlendFactorSourceAlpha;
        case 0x303: return MTLBlendFactorOneMinusSourceAlpha;
        case 0x304: return MTLBlendFactorDestinationAlpha;
        case 0x305: return MTLBlendFactorOneMinusDestinationAlpha;
        case 0x306: return MTLBlendFactorDestinationColor;
        case 0x307: return MTLBlendFactorOneMinusDestinationColor;
        default: return MTLBlendFactorOne;
    }
}

static uint64_t blendKey(int gl) { return gl <= 1 ? (uint64_t)gl : (uint64_t)((gl - 0x300 + 2) & 15); }

static id<MTLRenderPipelineState> pipeline(int shader, int blend, int src, int dst, int srcAlpha, int dstAlpha) {
    const uint64_t key = (uint64_t)shader | ((uint64_t)blend << 4) | (blendKey(src) << 8) | (blendKey(dst) << 12)
        | (blendKey(srcAlpha) << 16) | (blendKey(dstAlpha) << 20);
    id<MTLRenderPipelineState> state = pipelines[@(key)];
    if (state) return state;
    static NSString *const fragments[SHADER_COUNT] = { @"f_default", @"f_bilinear", @"f_ffmpeg", @"f_layer", @"f_distance_field" };
    MTLRenderPipelineDescriptor *d = [MTLRenderPipelineDescriptor new];
    d.vertexFunction = [library newFunctionWithName:shader == SHADER_DEFAULT ? @"v_default" : @"v_custom"];
    d.fragmentFunction = [library newFunctionWithName:fragments[shader < SHADER_COUNT ? shader : 0]];
    // SpriteBatch vertex: x, y, packed ABGR colour, u, v
    MTLVertexDescriptor *v = [MTLVertexDescriptor vertexDescriptor];
    v.attributes[0].format = MTLVertexFormatFloat2; v.attributes[0].offset = 0;
    v.attributes[1].format = MTLVertexFormatUChar4Normalized; v.attributes[1].offset = 8;
    v.attributes[2].format = MTLVertexFormatFloat2; v.attributes[2].offset = 12;
    v.layouts[0].stride = 20;
    d.vertexDescriptor = v;
    MTLRenderPipelineColorAttachmentDescriptor *c = d.colorAttachments[0];
    // Render target textures use the drawable's format so one pipeline serves both.
    c.pixelFormat = MTLPixelFormatBGRA8Unorm;
    c.blendingEnabled = blend ? YES : NO;
    c.sourceRGBBlendFactor = blendFactor(src);
    c.destinationRGBBlendFactor = blendFactor(dst);
    c.sourceAlphaBlendFactor = blendFactor(srcAlpha);
    c.destinationAlphaBlendFactor = blendFactor(dstAlpha);
    NSError *error = nil;
    state = [device newRenderPipelineStateWithDescriptor:d error:&error];
    if (!state) { NSLog(@"[metal] pipeline failed: %@", error); return nil; }
    pipelines[@(key)] = state;
    return state;
}

static id<MTLSamplerState> sampler(int minFilter, int magFilter, int wrapS, int wrapT) {
    const uint64_t key = (uint64_t)minFilter | ((uint64_t)magFilter << 16) | ((uint64_t)wrapS << 32) | ((uint64_t)wrapT << 48);
    id<MTLSamplerState> state = samplers[@(key)];
    if (state) return state;
    MTLSamplerDescriptor *d = [MTLSamplerDescriptor new];
    // GL_NEAREST 0x2600, GL_LINEAR 0x2601, GL_{NEAREST,LINEAR}_MIPMAP_{NEAREST,LINEAR} 0x2700-0x2703
    d.minFilter = (minFilter == 0x2600 || minFilter == 0x2700 || minFilter == 0x2702) ? MTLSamplerMinMagFilterNearest : MTLSamplerMinMagFilterLinear;
    d.magFilter = magFilter == 0x2600 ? MTLSamplerMinMagFilterNearest : MTLSamplerMinMagFilterLinear;
    d.mipFilter = minFilter >= 0x2700 ? (minFilter >= 0x2702 ? MTLSamplerMipFilterLinear : MTLSamplerMipFilterNearest) : MTLSamplerMipFilterNotMipmapped;
    // GL_REPEAT 0x2901, GL_MIRRORED_REPEAT 0x8370, otherwise clamp
    d.sAddressMode = wrapS == 0x2901 ? MTLSamplerAddressModeRepeat : wrapS == 0x8370 ? MTLSamplerAddressModeMirrorRepeat : MTLSamplerAddressModeClampToEdge;
    d.tAddressMode = wrapT == 0x2901 ? MTLSamplerAddressModeRepeat : wrapT == 0x8370 ? MTLSamplerAddressModeMirrorRepeat : MTLSamplerAddressModeClampToEdge;
    state = [device newSamplerStateWithDescriptor:d];
    samplers[@(key)] = state;
    return state;
}

void *bm_texture_create(int width, int height, int kind, int mipmapped) {
    const MTLPixelFormat format = kind == KIND_RGBA ? MTLPixelFormatRGBA8Unorm : kind == KIND_TARGET ? MTLPixelFormatBGRA8Unorm
        : kind == KIND_LUMINANCE_ALPHA ? MTLPixelFormatRG8Unorm : MTLPixelFormatR8Unorm;
    MTLTextureDescriptor *d = [MTLTextureDescriptor texture2DDescriptorWithPixelFormat:format width:width height:height mipmapped:mipmapped ? YES : NO];
    d.usage = kind == KIND_TARGET ? (MTLTextureUsageShaderRead | MTLTextureUsageRenderTarget) : MTLTextureUsageShaderRead;
    d.storageMode = kind == KIND_TARGET ? MTLStorageModePrivate : MTLStorageModeShared;
    // GL_ALPHA samples as (0,0,0,a), GL_LUMINANCE_ALPHA as (l,l,l,a), GL_LUMINANCE as (l,l,l,1)
    if (kind == KIND_ALPHA) d.swizzle = MTLTextureSwizzleChannelsMake(MTLTextureSwizzleZero, MTLTextureSwizzleZero, MTLTextureSwizzleZero, MTLTextureSwizzleRed);
    if (kind == KIND_LUMINANCE_ALPHA) d.swizzle = MTLTextureSwizzleChannelsMake(MTLTextureSwizzleRed, MTLTextureSwizzleRed, MTLTextureSwizzleRed, MTLTextureSwizzleGreen);
    if (kind == KIND_LUMINANCE) d.swizzle = MTLTextureSwizzleChannelsMake(MTLTextureSwizzleRed, MTLTextureSwizzleRed, MTLTextureSwizzleRed, MTLTextureSwizzleOne);
    id<MTLTexture> texture = [device newTextureWithDescriptor:d];
    return texture ? (__bridge_retained void *)texture : NULL;
}

/**
 * Uploads GL-format pixels. RGB888, RGB565 and RGBA4444 have no Metal equivalent and are widened to RGBA in
 * strips, so large images never leave big freed blocks behind in malloc. Safe to call from any thread.
 */
void bm_texture_upload(void *texture, int x, int y, int width, int height, int glFormat, int glType, const uint8_t *pixels) {
    id<MTLTexture> t = (__bridge id<MTLTexture>)texture;
    if (glType == 0x1401 && (glFormat == 0x1908 || glFormat == 0x1906 || glFormat == 0x1909 || glFormat == 0x190A)) {
        // RGBA, ALPHA, LUMINANCE, LUMINANCE_ALPHA as stored
        const size_t bpp = glFormat == 0x1908 ? 4 : glFormat == 0x190A ? 2 : 1;
        [t replaceRegion:MTLRegionMake2D(x, y, width, height) mipmapLevel:0 withBytes:pixels bytesPerRow:(size_t)width * bpp];
        return;
    }
    const size_t bpp = glFormat == 0x1907 && glType == 0x1401 ? 3 : 2;
    uint32_t *strip = malloc((size_t)width * STRIP_ROWS * 4);
    for (int row = 0; row < height; row += STRIP_ROWS) {
        const int rows = MIN(STRIP_ROWS, height - row);
        const size_t n = (size_t)width * rows;
        const uint8_t *src = pixels + (size_t)row * width * bpp;
        if (bpp == 3) {
            for (size_t i = 0; i < n; i++) strip[i] = src[i * 3] | (src[i * 3 + 1] << 8) | (src[i * 3 + 2] << 16) | 0xFF000000u;
        } else if (glType == 0x8363) { // RGB565
            const uint16_t *s = (const uint16_t *)src;
            for (size_t i = 0; i < n; i++) {
                const uint32_t r = (s[i] >> 11) & 31, g = (s[i] >> 5) & 63, b = s[i] & 31;
                strip[i] = (r * 255 / 31) | ((g * 255 / 63) << 8) | ((b * 255 / 31) << 16) | 0xFF000000u;
            }
        } else { // RGBA4444
            const uint16_t *s = (const uint16_t *)src;
            for (size_t i = 0; i < n; i++) {
                strip[i] = (((s[i] >> 12) & 15) * 17) | ((((s[i] >> 8) & 15) * 17) << 8) | ((((s[i] >> 4) & 15) * 17) << 16) | ((s[i] & 15) * 17 << 24);
            }
        }
        [t replaceRegion:MTLRegionMake2D(x, y + row, width, rows) mipmapLevel:0 withBytes:strip bytesPerRow:(size_t)width * 4];
    }
    free(strip);
}

/** Returns a mipmapped copy of the texture with the chain generated on the GPU (glGenerateMipmap). */
void *bm_texture_mipmapped(void *texture, int kind) {
    id<MTLTexture> t = (__bridge id<MTLTexture>)texture;
    if (t.mipmapLevelCount > 1) { CFRetain(texture); return texture; }
    id<MTLTexture> m = (__bridge_transfer id<MTLTexture>)bm_texture_create((int)t.width, (int)t.height, kind, 1);
    if (!m) { CFRetain(texture); return texture; }
    id<MTLCommandBuffer> b = [queue commandBuffer];
    id<MTLBlitCommandEncoder> blit = [b blitCommandEncoder];
    [blit copyFromTexture:t sourceSlice:0 sourceLevel:0 toTexture:m destinationSlice:0 destinationLevel:0 sliceCount:1 levelCount:1];
    [blit generateMipmapsForTexture:m];
    [blit endEncoding];
    [b commit];
    return (__bridge_retained void *)m;
}

void bm_texture_release(void *texture) { if (texture) CFRelease(texture); }

static int targetHeight(void) { return target ? (int)target.height : drawableHeight; }
static int targetWidth(void) { return target ? (int)target.width : drawableWidth; }

static void beginEncoder(void) {
    if (encoder || !commands) return;
    id<MTLTexture> texture = target ? target : drawable.texture;
    if (!texture) return;
    MTLRenderPassDescriptor *pass = [MTLRenderPassDescriptor renderPassDescriptor];
    pass.colorAttachments[0].texture = texture;
    pass.colorAttachments[0].loadAction = pendingClear ? MTLLoadActionClear : MTLLoadActionLoad;
    pass.colorAttachments[0].clearColor = clearColor;
    pass.colorAttachments[0].storeAction = MTLStoreActionStore;
    encoder = [commands renderCommandEncoderWithDescriptor:pass];
    pendingClear = NO;
}

/** Ends the current pass, running a pending clear even if nothing was drawn. */
static void endEncoder(void) {
    if (!encoder && pendingClear) beginEncoder();
    if (encoder) { [encoder endEncoding]; encoder = nil; }
}

int bm_begin_frame(int width, int height) {
    @autoreleasepool {
        dispatch_semaphore_wait(inflight, DISPATCH_TIME_FOREVER);
        if (width != drawableWidth || height != drawableHeight) {
            drawableWidth = width;
            drawableHeight = height;
            layer.drawableSize = CGSizeMake(width, height);
        }
        if (!layer.opaque) layer.opaque = YES;
        drawable = [layer nextDrawable];
        if (!drawable) { dispatch_semaphore_signal(inflight); return 0; }
        frameSlot = (frameSlot + 1) % FRAMES;
        vertexOffset = 0;
        commands = [queue commandBuffer];
        target = nil;
        pendingClear = YES;
        clearColor = MTLClearColorMake(0, 0, 0, 1);
        return 1;
    }
}

/** glBindFramebuffer: texture = colour attachment, NULL = the window. */
void bm_set_target(void *texture) {
    id<MTLTexture> t = (__bridge id<MTLTexture>)texture;
    if (t == target) return;
    endEncoder();
    target = t;
}

/** glClear(GL_COLOR_BUFFER_BIT) on the current target */
void bm_clear(float r, float g, float b, float a) {
    if (encoder) { [encoder endEncoding]; encoder = nil; }
    pendingClear = YES;
    clearColor = MTLClearColorMake(r, g, b, a);
}

/**
 * params: shader, blend, src, dst, srcAlpha, dstAlpha, minFilter, magFilter, wrapS, wrapT,
 *         scissor, sx, sy, sw, sh, viewport, vx, vy, vw, vh (GL rectangles: origin bottom-left)
 */
void bm_draw(const float *vertices, int quads, void *texture, const float *matrix, const int *p, const float *uniforms) {
    if (!commands || !texture || quads <= 0) return;
    const size_t bytes = (size_t)quads * 4 * 20;
    if (vertexOffset + bytes > VERTEX_BYTES) return;
    beginEncoder();
    if (!encoder) return;
    id<MTLRenderPipelineState> state = pipeline(p[0], p[1], p[2], p[3], p[4], p[5]);
    if (!state) return;
    const int tw = targetWidth(), th = targetHeight();
    // GL framebuffer textures are stored bottom row first and the engine flips them when drawing, so targets
    // are rendered upside down: GL y then equals the texture row and needs no conversion.
    const BOOL flip = target != nil;
    MTLScissorRect scissor = { 0, 0, (NSUInteger)tw, (NSUInteger)th };
    if (p[10]) {
        const int x0 = MAX(0, p[11]), x1 = MIN(tw, p[11] + p[13]);
        const int top = flip ? p[12] : th - (p[12] + p[14]);
        const int y0 = MAX(0, top), y1 = MIN(th, top + p[14]);
        if (x1 <= x0 || y1 <= y0) return;
        scissor = (MTLScissorRect){ (NSUInteger)x0, (NSUInteger)y0, (NSUInteger)(x1 - x0), (NSUInteger)(y1 - y0) };
    }
    MTLViewport viewport = { 0, 0, tw, th, 0, 1 };
    if (p[15]) viewport = (MTLViewport){ p[16], flip ? p[17] : th - (p[17] + p[19]), p[18], p[19], 0, 1 };
    float projection[16];
    memcpy(projection, matrix, sizeof(projection));
    if (flip) { projection[1] = -projection[1]; projection[5] = -projection[5]; projection[9] = -projection[9]; projection[13] = -projection[13]; }
    memcpy((uint8_t *)vertexBuffers[frameSlot].contents + vertexOffset, vertices, bytes);
    [encoder setRenderPipelineState:state];
    [encoder setViewport:viewport];
    [encoder setScissorRect:scissor];
    [encoder setVertexBuffer:vertexBuffers[frameSlot] offset:vertexOffset atIndex:0];
    [encoder setVertexBytes:projection length:sizeof(projection) atIndex:1];
    [encoder setFragmentTexture:(__bridge id<MTLTexture>)texture atIndex:0];
    [encoder setFragmentSamplerState:sampler(p[6], p[7], p[8], p[9]) atIndex:0];
    if (p[0] == SHADER_DISTANCE_FIELD) [encoder setFragmentBytes:uniforms length:48 atIndex:0];
    [encoder drawIndexedPrimitives:MTLPrimitiveTypeTriangle indexCount:(NSUInteger)quads * 6 indexType:MTLIndexTypeUInt16
                       indexBuffer:indices indexBufferOffset:0];
    vertexOffset += (bytes + 255) & ~(size_t)255;
}

/**
 * glReadPixels on the window: RGBA rows bottom-up like GL. Submits what was drawn so far, waits for it, and
 * continues the frame in a new command buffer.
 */
int bm_read_pixels(int x, int y, int width, int height, uint8_t *dst) {
    if (!commands || target || width <= 0 || height <= 0) return 0;
    @autoreleasepool {
        endEncoder();
        const size_t rowBytes = (size_t)width * 4;
        id<MTLBuffer> buffer = [device newBufferWithLength:rowBytes * height options:MTLResourceStorageModeShared];
        const int top = drawableHeight - (y + height);
        if (x < 0 || top < 0 || x + width > drawableWidth || top + height > drawableHeight) return 0;
        id<MTLBlitCommandEncoder> blit = [commands blitCommandEncoder];
        [blit copyFromTexture:drawable.texture sourceSlice:0 sourceLevel:0 sourceOrigin:MTLOriginMake(x, top, 0)
                   sourceSize:MTLSizeMake(width, height, 1) toBuffer:buffer destinationOffset:0
       destinationBytesPerRow:rowBytes destinationBytesPerImage:rowBytes * height];
        [blit endEncoding];
        [commands commit];
        [commands waitUntilCompleted];
        commands = [queue commandBuffer];
        const uint8_t *src = buffer.contents;
        for (int row = 0; row < height; row++) {
            const uint8_t *s = src + (size_t)(height - 1 - row) * rowBytes;
            uint8_t *d = dst + (size_t)row * rowBytes;
            for (int i = 0; i < width; i++) {
                d[i * 4] = s[i * 4 + 2]; d[i * 4 + 1] = s[i * 4 + 1]; d[i * 4 + 2] = s[i * 4]; d[i * 4 + 3] = s[i * 4 + 3];
            }
        }
        return 1;
    }
}

void bm_end_frame(void) {
    @autoreleasepool {
        if (!commands) return;
        endEncoder();
        target = nil;
        // Skins leave alpha below 1 where they draw translucent images over nothing, and Core Animation composites
        // the drawable with that alpha even on an opaque layer (the desktop showed through the select screen's
        // guide bar). Only the alpha channel is filled, so the frame's colours are untouched.
        if (opaquePipeline) {
            beginEncoder();
            if (encoder) {
                [encoder setRenderPipelineState:opaquePipeline];
                [encoder drawPrimitives:MTLPrimitiveTypeTriangle vertexStart:0 vertexCount:3];
            }
            endEncoder();
        }
        [commands presentDrawable:drawable];
        dispatch_semaphore_t semaphore = inflight;
        [commands addCompletedHandler:^(id<MTLCommandBuffer> b) { dispatch_semaphore_signal(semaphore); }];
        [commands commit];
        commands = nil;
        drawable = nil;
    }
}
