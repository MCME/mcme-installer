// How far the world is drawn, for the shaders that need to know: the eye
// (fire_eye.glsl), the sky (core/sky.fsh) and particles (core/particle.vsh).

// The server's view distance, 15 chunks, in blocks: chunks past it are never
// sent, so nothing in them is drawn.
#define SERVER_VIEW_DISTANCE 240.0

// Whether a distant terrain mod draws the world past the render distance,
// from the render distance fog's start, which they move far off: Distant
// Horizons to 4.2e14 blocks, with vanilla fog switched off in its settings
// (its default), and Voxy to 1e9. Nothing else moves it past 1e8.
bool farTerrain(float renderFogStart) {
    return renderFogStart > 1.0e8;
}
