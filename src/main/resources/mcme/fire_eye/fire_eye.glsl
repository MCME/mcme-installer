// The fire eye: a burning ball some FIRE_RADIUS blocks round its block (after
// Shadertoy wdVXWR), with a dark crust drifting up over it and a slit pupil
// that looks about, in a fiery almond fixed in the world, with flames
// streaming out from behind it and a wide glow. All of it is ray traced per
// pixel on camera-facing quads that its vertex part (fire_eye_main.glsl)
// makes of its model's faces; fireColor() is its colour there.
//
// Shared by vanilla's terrain.fsh and Sodium's block_layer_opaque.fsh. Pos is
// the fragment's position relative to the camera.
//
// Shader packs can't draw the eye's wide glow on quads: theirs cut off the fog
// and clouds behind anything translucent. They define FIRE_NO_GLOW, so that
// fireColor() is only the eye, and add fireGlowLight() over their finished
// scene instead.

uint fireHash(ivec4 p) {
    uint h = uint(p.x) * 73856093u ^ uint(p.y) * 19349663u ^ uint(p.z) * 83492791u ^ uint(p.w) * 2654435761u;
    h ^= h >> 13;
    h *= 0x5bd1e995u;
    h ^= h >> 15;
    return h;
}

float fireRand(ivec4 p) {
    return float(fireHash(p) & 0xFFFFu) / 65535.0;
}

// Value noise on the integer lattice, smoothly interpolated.
float fireValueNoise(vec3 p, int salt) {
    ivec3 i = ivec3(floor(p));
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n = 0.0;
    for (int k = 0; k < 8; k++) {
        ivec3 o = ivec3(k & 1, (k >> 1) & 1, (k >> 2) & 1);
        vec3 w = mix(1.0 - f, f, vec3(o));
        n += w.x * w.y * w.z * fireRand(ivec4(i + o, salt));
    }
    return n;
}

vec3 fireShade(float x) {
    return vec3(1.0, 0.0, 0.0) * x
         + vec3(1.0, 1.0, 0.0) * clamp(x - 0.5, 0.0, 1.0)
         + vec3(1.0, 1.0, 1.0) * clamp(x - 0.7, 0.0, 1.0);
}

mat3 fireRotate(float a, vec3 v) {
    float c = cos(a);
    vec3 ci = (1.0 - c) * v;
    vec3 s = sin(a) * v;
    return mat3(ci.x * v.x + c, ci.x * v.y + s.z, ci.x * v.z - s.y,
                ci.y * v.x - s.z, ci.y * v.y + c, ci.y * v.z + s.x,
                ci.z * v.x + s.y, ci.z * v.y - s.x, ci.z * v.z + c);
}

// In place of the original's noise texture: white noise on a lattice of
// FIRE_CELLS per unit, linearly filtered like a texture. Where its cells get
// smaller than a pixel (`footprint` cells to one) it fades to its average, as
// a mipmapped texture does.
float fireNoise(vec2 uv, float footprint, int layer) {
    vec2 g = uv * FIRE_CELLS;
    ivec2 c = ivec2(floor(g));
    vec2 f = fract(g);
    float n = mix(mix(fireRand(ivec4(c, layer, 190)), fireRand(ivec4(c + ivec2(1, 0), layer, 190)), f.x),
                  mix(fireRand(ivec4(c + ivec2(0, 1), layer, 190)), fireRand(ivec4(c + ivec2(1, 1), layer, 190)), f.x), f.y);
    return mix(0.5, n, 1.0 / max(footprint, 1.0));
}

// How far the eye's block hands over to the sky's eye, at distance blocks
// from the camera: 0 well inside FIRE_HANDOVER, 1 at it - over its last chunk.
float fireHandover(float distance) {
    return smoothstep(FIRE_HANDOVER - 16.0, FIRE_HANDOVER, distance);
}

// The glow round the ball, unpixelated: one glow, orange by the ball,
// reddening and easing away to nothing by FIRE_GLOW radii, and a second,
// larger, fainter one over FIRE_HALO radii. passSmooth is how close the view
// ray passes the eye's centre, in radii.
vec3 fireBallGlow(float passSmooth) {
    vec3 glowShade = mix(fireShade(0.9), vec3(1.0, 0.55, 0.3), smoothstep(1.0, 2.5, passSmooth));
    return FIRE_GLOW_STRENGTH * glowShade * exp(-max(passSmooth - 0.9, 0.0) * FIRE_GLOW_FALLOFF)
         * (1.0 - smoothstep(FIRE_GLOW * 0.25, FIRE_GLOW, passSmooth));
}

// The second glow thins from the start, with no flat middle to end in a rim,
// and eases into nothing; and it is a light, warm orange - in the resource
// pack it is blended over the sky, not added to it, and a deep red would
// darken a pale sky into a disc.
vec3 fireHalo(float passSmooth) {
    float h = min(passSmooth / FIRE_HALO, 1.0);
    return FIRE_HALO_STRENGTH * vec3(1.0, 0.6, 0.35) * (1.0 - h) * (1.0 - h) * (1.0 - h);
}

// Both glows, as light to add along view ray rayDir (normalised), for the eye
// centred at centre; both relative to the camera, in blocks. None over the
// ball itself.
vec3 fireGlowLight(vec3 rayDir, vec3 centre) {
    vec3 eye = -centre / FIRE_RADIUS;
    float passSmooth = length(eye + rayDir * max(dot(-eye, rayDir), 0.0));
    return (fireBallGlow(passSmooth) + fireHalo(passSmooth)) * smoothstep(0.82, 1.0, passSmooth);
}

// The eye's colour on its layer's quad, alpha 0 where it draws nothing. Its
// own light: no shading, no light map.
vec4 fireColor() {
    vec3 dir = normalize(Pos);
    vec3 eye = -fireCentre / FIRE_RADIUS;      // the camera, in radii, from the centre
    float time = fireTime * FIRE_SPEED;
    // the view's frame: x right, y up, z towards the camera
    vec3 axisZ = normalize(eye);
    vec3 up = abs(axisZ.y) > 0.99 ? vec3(0.0, 0.0, 1.0) : vec3(0.0, 1.0, 0.0);
    vec3 axisX = normalize(cross(up, axisZ));
    vec3 axisY = cross(axisZ, axisX);
    // a pixel's width on the ball's surface, in radians round it
    float pixel = length(fwidth(dir)) * length(eye);

    // where the ray crosses the plane through the centre, facing the camera:
    // the pupil is drawn there, in radii
    float facing = dot(dir, -axisZ);
    vec3 crossing = eye + dir * (length(eye) / max(facing, 1.0e-3));
    vec2 plane = facing > 0.0 ? vec2(dot(crossing, axisX), dot(crossing, axisY)) : vec2(1.0e3);
    // pixelated, as Minecraft's textures are: that plane is cut into squares
    // of FIRE_PIXEL blocks, each traced once, through its middle, for the
    // ball, its pupil and its glow
    vec3 rayDir = dir;
    float cell = FIRE_PIXEL / FIRE_RADIUS;
    if (facing > 0.0) {
        plane = (floor(plane / cell) + 0.5) * cell;
        dir = normalize(axisX * plane.x + axisY * plane.y - eye);
    }
    pixel = max(pixel, cell);
    // how close the true, unpixelated ray passes the centre, for the glows
    float passSmooth = length(eye + rayDir * max(dot(-eye, rayDir), 0.0));
    float along = max(dot(-eye, dir), 0.0);
    float pass = length(eye + dir * along);    // how close the ray passes the centre

    // ---- the ball: the original's nested burning spheres
    float intensity = 0.0;
    float crust = 0.0;
    if (pass < 1.0 && fireLayer == 0) {
        mat3 texStep = fireRotate(radians(11.0) * time, normalize(vec3(0.3, -0.7, 0.1)));
        mat3 windStep = fireRotate(radians(25.0) * time, vec3(1.0, 0.0, 0.0));
        mat3 tex = texStep;
        mat3 wind = windStep;
        for (int i = 1; i < 10; i++) {
            float r = 1.0 - float(i) / 40.0;
            float b = dot(eye, dir);
            float disc = b * b - (dot(eye, eye) - r * r);
            if (disc < 0.0) break;                // smaller ones are missed too
            float t = -b - sqrt(disc);
            if (t < 0.0) t = -b + sqrt(disc);     // from inside: its far side
            if (t < 0.0) continue;
            vec3 hit = eye + dir * t;
            vec3 local = vec3(dot(hit, axisX), dot(hit, axisY), dot(hit, axisZ));
            vec3 n = normalize(tex * wind * local);
            vec2 uv = vec2(atan(n.z, n.x) / radians(90.0), n.y) / float(i);
            float footprint = FIRE_CELLS * pixel / (radians(90.0) * float(i));
            float alpha = fireNoise(uv, footprint, i);
            float cut = 1.0 - float(i) / 6.0;
            intensity += smoothstep(cut - 0.03, cut + 0.03, alpha) * 0.8 * alpha * max(0.0, local.z);
            if (i == 1) {
                // a dark crust on the surface, in upright streaks, drifting
                // up; thinning towards the rim, which keeps burning
                vec3 c = fireRotate(radians(FIRE_CRUST_SPEED) * time, vec3(1.0, 0.0, 0.0)) * local;
                float streaks = fireValueNoise(vec3(c.x * 7.0, c.y * 2.5, c.z * 7.0), 230) * 0.6
                              + fireValueNoise(vec3(c.x * 15.0, c.y * 5.0, c.z * 15.0), 231) * 0.4;
                crust = smoothstep(0.48, 0.68, streaks) * smoothstep(0.15, 0.6, local.z);
            }
            tex = texStep * tex;
            wind = windStep * wind;
        }
    }
    vec3 ball = fireShade(intensity) * (1.0 - crust * FIRE_CRUST);
    // a soft edge, fading over its outer rim; and a glow round it, orange by
    // the ball, reddening and easing away to nothing by FIRE_GLOW radii
    float ballCover = 1.0 - smoothstep(0.82, 1.0, pass);
    // (both glows are taken from the true ray, unpixelated, so they fade
    // smoothly)
#ifdef FIRE_NO_GLOW
    vec3 ballGlow = vec3(0.0);
#else
    vec3 ballGlow = fireBallGlow(passSmooth);
#endif

    // ---- the slit pupil: tall, black, its edges flickering, a bright rim.
    // It looks about: every FIRE_LOOK_HOLD seconds it moves to a new spot -
    // further sideways than up or down, now and then back to the middle - and
    // holds there, narrowing as it turns away.
    float glances = fireTime / FIRE_LOOK_HOLD;
    int glance = int(floor(glances));
    vec2 lookFrom = fireRand(ivec4(glance - 1, 2, 0, 270)) < 0.3 ? vec2(0.0)
                  : vec2(fireRand(ivec4(glance - 1, 0, 0, 270)), fireRand(ivec4(glance - 1, 1, 0, 270))) * 2.0 - 1.0;
    vec2 lookTo = fireRand(ivec4(glance, 2, 0, 270)) < 0.3 ? vec2(0.0)
                : vec2(fireRand(ivec4(glance, 0, 0, 270)), fireRand(ivec4(glance, 1, 0, 270))) * 2.0 - 1.0;
    float dart = smoothstep(0.0, FIRE_LOOK_DART / FIRE_LOOK_HOLD, fract(glances));
    vec2 look = mix(lookFrom, lookTo, dart) * vec2(FIRE_LOOK, FIRE_LOOK * 0.5);
    vec2 pupil = plane - look;
    float flicker = fireValueNoise(vec3(pupil.y * 6.0, time * 2.0, 0.0), 240);
    float slitHalf = FIRE_SLIT_WIDTH * sqrt(max(1.0 - (pupil.y / FIRE_SLIT_HEIGHT) * (pupil.y / FIRE_SLIT_HEIGHT), 0.0))
                   * mix(0.8, 1.2, flicker) * sqrt(max(1.0 - dot(look, look), 0.3));
    float slit = 1.0 - smoothstep(slitHalf * 0.7, slitHalf, abs(pupil.x));
    float slitRim = (1.0 - smoothstep(slitHalf, slitHalf * 2.2, abs(pupil.x))) * step(0.001, slitHalf) - slit;
    ball = mix(ball, vec3(0.03, 0.0, 0.0), slit) + fireShade(1.5) * 0.45 * max(slitRim, 0.0);

    // ---- a corona: flames streaming outward from behind the ball, all round
    // its rim, longest towards the almond's tips so the two run together;
    // pixelated with the ball, its roots hidden behind it
    float coronaR = length(plane);
    float coronaAngle = atan(plane.y, plane.x);
    vec3 cp = vec3(cos(coronaAngle) * 7.0, sin(coronaAngle) * 7.0,
                   log(max(coronaR, 0.5)) * 3.0 - time * FIRE_CORONA_SPEED);
    float tongues = smoothstep(0.35, 0.8, fireValueNoise(cp, 280) * 0.6 + fireValueNoise(cp * 2.2 + 3.0, 281) * 0.4);
    float coronaReach = mix(1.0 + (FIRE_CORONA - 1.0) * 0.5, FIRE_CORONA, abs(cos(coronaAngle)));
    float coronaHeat = tongues * (1.0 - smoothstep(1.0, coronaReach, coronaR)) * FIRE_CORONA_STRENGTH;
    vec3 corona = fireShade(coronaHeat * 1.8);

    // ---- the almond round it, fixed in the world: flat in the plane through
    // the centre facing along x, running north-south - so it turns edge-on
    // seen from the north or south. Pixelated on its own plane. Flames stream
    // in towards the ball, faster as they near it, its lids licked by them.
    float almondT = abs(rayDir.x) > 1.0e-4 ? -eye.x / rayDir.x : -1.0;
    vec2 almondPos = almondT > 0.0 ? (eye + rayDir * almondT).zy : vec2(1.0e3);
    almondPos = (floor(almondPos / cell) + 0.5) * cell;
    float rho = length(almondPos / vec2(FIRE_EYE_WIDTH, FIRE_EYE_HEIGHT));
    float angle = atan(almondPos.y, almondPos.x);
    vec3 fp = vec3(cos(angle) * 5.0, sin(angle) * 5.0, log(max(rho, 0.03)) * 2.5 + time * FIRE_EYE_PULL);
    float flames = fireValueNoise(fp, 220) * 0.6 + fireValueNoise(fp * 2.1 + 7.0, 221) * 0.4;
    float lid = FIRE_EYE_HEIGHT * max(1.0 - (almondPos.x / FIRE_EYE_WIDTH) * (almondPos.x / FIRE_EYE_WIDTH), 0.0);
    float inEye = abs(almondPos.y) / max(lid, 1.0e-3) - (flames - 0.5) * 0.5;
    // soft lids; the tips fade only at their very ends
    float almond = (1.0 - smoothstep(0.15, 1.3, inEye))
                 * (1.0 - smoothstep(0.8, 1.0, abs(almondPos.x) / FIRE_EYE_WIDTH));
    // streaks of flame over a faint ground, brightest in by the ball
    float streaks = smoothstep(0.4, 0.85, flames);
    float heat = almond * (0.15 + streaks) * mix(1.5, 0.6, clamp(rho, 0.0, 1.0)) * FIRE_EYE_STRENGTH;
    // its ground and solidity fall away faster than its flames, so its edge
    // thins out rather than ending
    vec3 eyeFire = fireShade(heat) + vec3(0.12, 0.01, 0.0) * almond * almond;
    float eyeCover = almond * almond * 0.3;

    // the layers split the view: the eye's draws where the ball or almond is,
    // all its glow included; the glow's layers everywhere else. Those split
    // the pixels between them, each drawing every FIRE_GLOW_LAYERS-th in a
    // fixed pattern: drawn in whatever order, none can hide another (a layer
    // records its depth where it draws, and the game orders them by where the
    // camera stands)
    bool eyePart = passSmooth < max(1.05, FIRE_CORONA) || almond > 0.0;
    if ((fireLayer >= 1) == eyePart) return vec4(0.0);
    ivec2 screen = ivec2(gl_FragCoord.xy);
    int share = ((screen.x & 1) + 2 * (screen.y & 1) + 3 * ((screen.x >> 1) & 1)) % FIRE_GLOW_LAYERS;
    if (fireLayer >= 1 && share != fireLayer - 1) return vec4(0.0);

    // the ball, the almond in front of or behind it, and the glow round the
    // ball's edge; behind, the almond's flames run on over the ball's rim,
    // joining the two
    float b = dot(eye, rayDir);
    float disc = b * b - (dot(eye, eye) - 1.0);
    float ballT = disc > 0.0 ? -b - sqrt(disc) : 1.0e9;
    vec3 rgb;
    float cover = max(ballCover, eyeCover);
    if (almondT > 0.0 && almondT < ballT) {
        float eyeAlpha = clamp(max(max(eyeFire.r, max(eyeFire.g, eyeFire.b)), eyeCover), 0.0, 1.0);
        vec3 behind = ball * ballCover + (ballGlow + corona) * (1.0 - ballCover);
        rgb = eyeFire + behind * (1.0 - eyeAlpha);
    } else {
        float rim = smoothstep(0.55, 1.0, pass);
        rgb = mix(eyeFire + ballGlow + corona, ball + eyeFire * rim * 0.6, ballCover);
    }

    // the second, larger glow
#ifndef FIRE_NO_GLOW
    rgb += fireHalo(passSmooth) * (1.0 - cover);
#endif

    // a dither of under one colour step, so faint fades don't break into
    // bands - only where there is something, so that beyond the glow stays
    // exactly nothing and is discarded (a faint fragment still hides clouds
    // and particles behind it)
    float something = smoothstep(0.0, 4.0 / 255.0, max(rgb.r, max(rgb.g, rgb.b)));
    rgb = max(rgb + (fireRand(ivec4(screen, 0, 260)) - 0.5) / 255.0 * something, 0.0);

    // blended over the world: solid where the eye is, elsewhere as bright as
    // its brightest channel, so the world shows through the haze
    float a = clamp(max(max(rgb.r, max(rgb.g, rgb.b)), cover), 0.0, 1.0);
    return vec4(min(rgb / max(a, 1.0e-3), 1.0), a);
}
