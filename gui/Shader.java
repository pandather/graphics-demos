package gui;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL32.*;

import java.io.*;
import java.util.Scanner;

public class Shader {
	private int programID;
	private int vertexShader, geometryShader, fragmentShader;
	
	public Shader(String vertexShaderFilename, String fragmentShaderFilename) {
		this(vertexShaderFilename, null, fragmentShaderFilename);
	}
	
	public Shader(String vertexShaderFilename, String geometryShaderFilename, String fragmentShaderFilename) {
		programID = glCreateProgram();
		
		vertexShader = loadShader(vertexShaderFilename, GL_VERTEX_SHADER);
		if(geometryShaderFilename != null)
			geometryShader = loadShader(geometryShaderFilename, GL_GEOMETRY_SHADER);
		fragmentShader = loadShader(fragmentShaderFilename, GL_FRAGMENT_SHADER);

		glAttachShader(programID, vertexShader);
		if(geometryShaderFilename != null)
			glAttachShader(programID, geometryShader);
		glAttachShader(programID, fragmentShader);
		
		glLinkProgram(programID);
		
		if (glGetProgrami(programID, GL_LINK_STATUS) == GL_FALSE) {
			System.out.println("Could not link shader. Reason: " + glGetProgramInfoLog(programID, 1000));
			throw new RuntimeException("Could not link shader. Reason: " + glGetProgramInfoLog(programID, 1000));
		}
		
		glValidateProgram(programID);
		
		if (glGetProgrami(programID, GL_VALIDATE_STATUS) == GL_FALSE) {
			System.out.println("Could not validate shader. Reason: " + glGetProgramInfoLog(programID, 1000));
			throw new RuntimeException("Could not validate shader. Reason: " + glGetProgramInfoLog(programID, 1000));            
		}
	}
	
	private int loadShader(String filename, int shaderType) {
		int handle = glCreateShader(shaderType);
		glShaderSource(handle, fileToString(filename));
		glCompileShader(handle);
		if(glGetShaderi(handle, GL_COMPILE_STATUS) == GL_FALSE) {
			System.out.println((shaderType == GL_VERTEX_SHADER ? "Vertex" : shaderType == GL_GEOMETRY_SHADER ?  "Geometry" : "Fragment") + " shader compiled unsuccessfully.");
			System.out.println(glGetShaderInfoLog(handle));
			throw new RuntimeException((shaderType == GL_VERTEX_SHADER ? "Vertex" : shaderType == GL_GEOMETRY_SHADER ?  "Geometry" : "Fragment") + " shader compiled unsuccessfully.");
		}
		return handle;
	}
	
	private String fileToString(String filename) {
		Scanner input = null;
		try {
			input = new Scanner(new File(filename));
		} catch (FileNotFoundException e) {
			throw new RuntimeException("Shader input file not found. Path given was: " + filename);
		}
		StringBuffer output = new StringBuffer();
		while(input.hasNextLine())
			output.append(input.nextLine()).append('\n');
		input.close();
		return output.toString();
	}
	
	public int getProgramID() {
		return programID;
	}
	
	public void clean() {
		glDeleteProgram(programID);
		glDeleteShader(vertexShader);
		if(geometryShader != 0)
			glDeleteShader(geometryShader);
		glDeleteShader(fragmentShader);
	}
}
