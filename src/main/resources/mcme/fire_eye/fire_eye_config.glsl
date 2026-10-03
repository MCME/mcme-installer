// The fire eye's settings, shared by its vertex part (fire_eye_main.glsl) and
// its fragment part (fire_eye.glsl). See fire_eye.glsl for what it is.

// Where its sheet's descriptors start, along their row: the eye's own, then
// one per glow layer (assets/minecraft/textures/block/fire_eye.png).
#define FIRE_DESCRIPTOR_X 8

// Where the eye is - its block - for what draws it from afar: shader packs
// (patch_shaderpack.py) and, with a distant terrain mod, the sky (core/sky.fsh)...
#define FIRE_EYE_BLOCK ivec3(12447, 678, 2629)
// ...past this distance, in blocks, beyond which the eye's chunk is never sent
// and its block never drawn (far_terrain.glsl, imported first)
#define FIRE_HANDOVER SERVER_VIEW_DISTANCE

#define FIRE_RADIUS 8.3              // the ball's radius, in blocks; everything else scales with it
#define FIRE_GLOW 8.0                // how far the glow reaches, in radii; keep it past FIRE_EYE_WIDTH and under FIRE_HALO
#define FIRE_GLOW_FALLOFF 0.8        // how quickly it eases away from the ball; lower spreads it wider
#define FIRE_GLOW_STRENGTH 0.5       // how bright the glow round the ball is; it also dims what's behind
#define FIRE_PIXEL 0.25              // its pixels' size, in blocks
#define FIRE_HALO 11.0               // a second, fainter glow: how far it reaches, in radii...
#define FIRE_HALO_STRENGTH 0.3       // ...and how bright it is at the ball
#define FIRE_GLOW_LAYERS 4           // the glow is drawn in this many layers (2 or 4 split the pixels evenly; the sheet and model have 4)...
#define FIRE_GLOW_DEPTH 6.0          // ...spread over this many radii behind its centre
#define FIRE_CELLS 64.0              // how fine its flames are: the noise's cells per unit
#define FIRE_SPEED 1.0               // how fast they turn
#define FIRE_CRUST 0.85              // how dark the crust drifting over the ball is...
#define FIRE_CRUST_SPEED 18.0        // ...and how fast it drifts up, in degrees a second
#define FIRE_EYE_WIDTH 3.2           // the almond round the ball: half its width...
#define FIRE_EYE_HEIGHT 1.25         // ...and half its height, in radii
#define FIRE_EYE_PULL 0.6            // how fast its flames stream in towards the ball
#define FIRE_EYE_STRENGTH 1.1        // how bright they are
#define FIRE_CORONA 1.9              // flames streaming out from behind the ball: how far, in radii...
#define FIRE_CORONA_STRENGTH 1.0     // ...how bright...
#define FIRE_CORONA_SPEED 0.8        // ...and how fast they stream
#define FIRE_SLIT_WIDTH 0.16         // the pupil: half its width at the middle...
#define FIRE_SLIT_HEIGHT 0.6         // ...and half its height, in radii
#define FIRE_LOOK 0.4                // how far the pupil looks about, sideways, in radii (half that up and down)...
#define FIRE_LOOK_HOLD 6.0           // ...how long it holds each glance, in seconds...
#define FIRE_LOOK_DART 1.0           // ...and how long it takes to move to the next
