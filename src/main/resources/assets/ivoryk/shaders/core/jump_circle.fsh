#version 150 core

// Input uniforms desde el código Java
uniform vec3 u_color;
uniform float u_progress;
uniform float u_opacity;
uniform float u_thickness;

// Variables interpoladas
in vec2 f_position;

out vec4 fragColor;

/**
 * Jump Circles Fragment Shader
 * Estilo Thunderhack con SDF (Signed Distance Fields)
 * Compatible con GLSL 120/150 para máxima compatibilidad con dispositivos móviles
 */

// Constantes
const float FEATHER = 0.02; // Anti-aliasing suave
const float GLOW_INTENSITY = 0.8;

/**
 * Signed Distance Field para círculo
 * Calcula la distancia con signo desde un punto al círculo
 */
float circleSDF(vec2 pos, float radius) {
    return length(pos) - radius;
}

/**
 * SmoothStep mejorado para anti-aliasing ultra suave
 * Crea bordes suavísimos sin aliasing
 */
float smoothEdge(float dist, float width, float feather) {
    return smoothstep(width + feather, width - feather, dist);
}

/**
 * Filtra según la distancia para crear el anillo
 */
float ringMask(float dist, float thickness) {
    float outerEdge = smoothEdge(dist, 0.0, FEATHER);
    float innerEdge = 1.0 - smoothEdge(dist, -thickness, FEATHER);
    return outerEdge * innerEdge;
}

/**
 * Efecto de Glow/Bloom
 */
vec3 applyGlow(vec3 baseColor, float dist, float thickness) {
    // Glow exterior suave
    float glowFalloff = exp(-abs(dist) * 2.0);
    return baseColor + vec3(glowFalloff * GLOW_INTENSITY * 0.3);
}

void main() {
    // Normalizar coordenadas para radio máximo
    vec2 pos = f_position * u_progress;
    
    // Calcular SDF del círculo con grosor
    float dist = circleSDF(pos, u_progress);
    
    // Crear el anillo con anti-aliasing suave
    float ring = ringMask(dist, u_thickness);
    
    // Fade out suave según progreso
    float alphaCurve = sin(u_progress * 3.14159) * u_opacity;
    
    // Aplicar glow
    vec3 glowColor = applyGlow(u_color, dist, u_thickness);
    
    // Output final con alpha correcta
    fragColor = vec4(glowColor * ring, ring * alphaCurve * u_opacity);
    
    // Opcional: si se desea un efecto aún más thunderhack, agregar rim lighting
    // fragColor += vec4(0.2 * ring * sin(u_progress * 6.28), 0);
}
