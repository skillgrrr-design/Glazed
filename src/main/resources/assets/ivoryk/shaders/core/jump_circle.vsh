#version 150 core

/* Jump Circles Vertex Shader */

in vec3 Position;
in vec2 UV;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 f_position;

void main() {
    // Pasar las coordenadas UV como posición para el fragment shader
    f_position = UV;
    
    // Transformar posición al espacio de pantalla
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
