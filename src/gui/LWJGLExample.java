
package gui;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_MULTISAMPLE;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.nio.*;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

public class LWJGLExample implements Runnable {
	private long window;

	private Thread thread;
	private boolean running = false;

	private String title = "octree-example";

	private boolean resizable = false;
//	private int msaaSamples = 4;

	private int width = 1000;
	private int height = 1000;

	private boolean showFramerate = true;
	long frameTimer;
	int frames;
	
	long startTimer;

	private double rotate_x = 0;
	private double rotate_y = 0;
	private double rotate_z = 0;
	
	private byte maxDepth = 11;
	private boolean voxel = true;

	public void start() {
		running = true;
		thread = new Thread(this, title);
		thread.start();
	}

	public void run() {
		init();

		frameTimer = System.currentTimeMillis();
		
		Voxel voxelRoot = new Voxel();
//		makeFullCube(voxelRoot, maxDepth, 0);
		makeSphere(voxelRoot, maxDepth, 0, -0.5f, -0.5f, -0.5f);
//		makePacman(voxelRoot, maxDepth, 0, -0.5f, -0.5f, -0.5f);
//		makeGatling(voxelRoot, maxDepth, 0, -0.5f, -0.5f, -0.5f, 9, 0.3f, 0.03f, 0.15f, 0.03f, 0.03f);
//		makeDoubleHelix(voxelRoot, maxDepth, 0, -0.5f, -0.5f, -0.5f, 0.1f, 0.1f);
		
		Pixel pixelRoot = new Pixel();
//		makeFullSquare(pixelRoot, maxDepth, 0);
//		makeCircle(pixelRoot, maxDepth, 0, -0.5f, -0.5f);
//		makePacman(pixelRoot, maxDepth, 0, -0.5f, -0.5f);
		
		short vertices[] = new short[(int) (Integer.MAX_VALUE * ((voxel ? 6.0 : 4.0) / (voxel ? 9 : 7) / 2)) / 2];
		byte depths[] = new byte[vertices.length / (voxel ? 3 : 2)];
		int lengths[] = null;
		
		if(voxel)
			voxelRoot.render((short) -(1 << (maxDepth - 1)), (short) -(1 << (maxDepth - 1)), (short) -(1 << (maxDepth - 1)), maxDepth, (byte) 0, vertices, depths, lengths = new int[] {0, 0});
		else
			pixelRoot.render((short) -(1 << (maxDepth - 1)), (short) -(1 << (maxDepth - 1)), maxDepth, (byte) 0, vertices, depths, lengths = new int[] {0, 0});
				
		ShortBuffer verticesBuffer = BufferUtils.createShortBuffer(vertices.length);
		ByteBuffer depthsBuffer = BufferUtils.createByteBuffer(depths.length);
		
		int nodes = lengths[1];

		System.out.println((voxel ? "Voxels: " : "Pixels: ") + nodes);
		
		int vaoID = glGenVertexArrays();
		glBindVertexArray(vaoID);
		
		int vboID = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, vboID);
		glBufferData(GL_ARRAY_BUFFER, verticesBuffer.limit() * 2 + depthsBuffer.limit(), GL_DYNAMIC_DRAW);
		glBufferSubData(GL_ARRAY_BUFFER, 0, verticesBuffer);
		glBufferSubData(GL_ARRAY_BUFFER, verticesBuffer.limit() * 2, depthsBuffer);
		
		Shader shader;
		if(voxel)
			shader = new Shader("shaders/voxel.vertex", "shaders/voxel.geometry", "shaders/both.fragment");
		else
			shader = new Shader("shaders/pixel.vertex", "shaders/pixel.geometry", "shaders/both.fragment");
		
		glUseProgram(shader.getProgramID());
		int positionAttrib = glGetAttribLocation(shader.getProgramID(), "position");
		glEnableVertexAttribArray(positionAttrib);
		glVertexAttribPointer(positionAttrib, voxel ? 3 : 2, GL_SHORT, false, 0, 0);
		
		int depthAttrib = glGetAttribLocation(shader.getProgramID(), "depth");
		glEnableVertexAttribArray(depthAttrib);
		glVertexAttribIPointer(depthAttrib, 1, GL_BYTE, 0, verticesBuffer.limit() * 2);
		
		frameTimer = startTimer = System.currentTimeMillis();
		Matrix4f projection;
		FloatBuffer projectionMatrixBuffer = BufferUtils.createFloatBuffer(16);
		int factorMatrixLoc = glGetUniformLocation(shader.getProgramID(), "FactorMatrix");

		while (running) {
			nodes = lengths[1];
			
			verticesBuffer.clear();
			verticesBuffer.put(vertices, 0, lengths[0]);
			verticesBuffer.flip();
			
			depthsBuffer.clear();
			depthsBuffer.put(depths, 0, lengths[1]);
			depthsBuffer.flip();
			
			glBindBuffer(GL_ARRAY_BUFFER, vboID);
			glBufferData(GL_ARRAY_BUFFER, verticesBuffer.limit() * 2 + depthsBuffer.limit(), GL_DYNAMIC_DRAW);
			glBufferSubData(GL_ARRAY_BUFFER, 0, verticesBuffer);
			glBufferSubData(GL_ARRAY_BUFFER, verticesBuffer.limit() * 2, depthsBuffer);
			
			glVertexAttribPointer(positionAttrib, voxel ? 3 : 2, GL_SHORT, false, 0, 0);
			glVertexAttribIPointer(depthAttrib, 1, GL_BYTE, 0, verticesBuffer.limit() * 2);
			
			update();
			projection = new Matrix4f()
					.translate(0, 0, 0)
					.rotateX((float) Math.toRadians(rotate_x))
					.rotateY((float) Math.toRadians(rotate_y))
					.rotateZ((float) Math.toRadians(rotate_z))
					.scale((float) ((voxel ? 0.5 : 1.0) / (1 << (maxDepth - 1))));
			projection.get(projectionMatrixBuffer);
			glUniformMatrix4fv(factorMatrixLoc, false, projectionMatrixBuffer);
			render(nodes);
			printFramerate();
			if (glfwWindowShouldClose(window))
				running = false;
			
//			if(voxel) {
//				voxelRoot = new Voxel();
//			} else {
//				pixelRoot = new Pixel();
//			}
//			makeMovingPacman(voxelRoot, maxDepth, 0, -0.5f, -0.5f, -0.5f, System.currentTimeMillis() - startTimer);
//			makeMovingPacman(pixelRoot, maxDepth, 0, -0.5f, -0.5f, System.currentTimeMillis() - startTimer);
//			if(voxel)
//				voxelRoot.render((short) -(1 << (maxDepth - 1)), (short) -(1 << (maxDepth - 1)), (short) -(1 << (maxDepth - 1)), maxDepth, 0, vertices, colors, lengths = new int[] {0, 0});
//			else
//				pixelRoot.render((short) -(1 << (maxDepth - 1)), (short) -(1 << (maxDepth - 1)), maxDepth, 0, vertices, colors, lengths = new int[] {0, 0});
		}

		
		shader.clean();
		shader = null;
		
		glDeleteBuffers(vboID);
		glDeleteVertexArrays(vaoID);

		// Free the window callbacks and destroy the window
		glfwFreeCallbacks(window);
		glfwDestroyWindow(window);

		// Terminate GLFW and free the error callback
		glfwTerminate();
		glfwSetErrorCallback(null).free();
	}
	
	private void printFramerate() {
		frames++;
		if(System.currentTimeMillis() - frameTimer >= 1000) {
			if(showFramerate)
				System.out.println(frames * 1000.0 / (System.currentTimeMillis() - frameTimer));
			frameTimer = System.currentTimeMillis();
			frames = 0;
		}
	}

	private void init() {
		// Setup an error callback. The default implementation
		// will print the error message in System.err.
		GLFWErrorCallback.createPrint(System.err).set();

		// Initialize GLFW. Most GLFW functions will not work before doing this.
		if (!glfwInit())
			throw new IllegalStateException("Unable to initialize GLFW");

		// Configure GLFW
		glfwDefaultWindowHints(); // optional, the current window hints are already the default
		glfwWindowHint(GLFW_VISIBLE, GLFW_TRUE); // the window will stay hidden after creation
		glfwWindowHint(GLFW_RESIZABLE, resizable ? GLFW_TRUE : GLFW_FALSE); // the window will be resizable
//		glfwWindowHint(GLFW_SAMPLES, msaaSamples); // the window will be resizable

		// Create the window
		window = glfwCreateWindow(width, height, title, NULL, NULL);
		if (window == NULL)
			throw new RuntimeException("Failed to create the GLFW window");

		// Setup a key callback. It will be called every time a key is pressed, repeated
		// or released.
		glfwSetKeyCallback(window, new Input());

		// Get the thread stack and push a new frame
		try (MemoryStack stack = stackPush()) {
			IntBuffer pWidth = stack.mallocInt(1); // int*
			IntBuffer pHeight = stack.mallocInt(1); // int*

			// Get the window size passed to glfwCreateWindow
			glfwGetWindowSize(window, pWidth, pHeight);

			// Get the resolution of the primary monitor
			GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());

			// Center the window
			glfwSetWindowPos(window, (vidmode.width() - pWidth.get(0)) / 2, (vidmode.height() - pHeight.get(0)) / 2);
		} // the stack frame is popped automatically

		// Make the OpenGL context current
		glfwMakeContextCurrent(window);

		// Enable v-sync
		// glfwSwapInterval(1);

		// Make the window visible
		glfwShowWindow(window);

		GL.createCapabilities();

		glEnable(GL_DEPTH_TEST);
		glMatrixMode(GL_PROJECTION);
	}

	private void render(int numVertices) {
		glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // clear the framebuffer
		glLoadIdentity();

		glDrawArrays(GL_POINTS, 0, numVertices);

		glFlush();
		glfwSwapBuffers(window);
	}

	private void update() {
		glfwPollEvents();
		if (Input.isDown(GLFW_KEY_ESCAPE))
			glfwSetWindowShouldClose(window, true);
		if (Input.isDown(GLFW_KEY_W))
			rotate_x += 0.5;
		if (Input.isDown(GLFW_KEY_S))
			rotate_x -= 0.5;
		if (Input.isDown(GLFW_KEY_A))
			rotate_y -= 0.5;
		if (Input.isDown(GLFW_KEY_D))
			rotate_y += 0.5;
		if (Input.isDown(GLFW_KEY_Q))
			rotate_z -= 0.5;
		if (Input.isDown(GLFW_KEY_E))
			rotate_z += 0.5;
		if (Input.isPressed(GLFW_KEY_F))
			showFramerate = !showFramerate;
	}

	public static void main(String[] args) {
		new LWJGLExample().start();
	}

	@SuppressWarnings("unused")
	private void makeSphere(Voxel node, int maxDepth, int currDepth, float x, float y, float z) {
		if(maxDepth == currDepth) {
			node.children = new Voxel[0];
			return;
		}
		float width = (float) (1 / Math.pow(2, currDepth));
		float height = (float) (1 / Math.pow(2, currDepth));
		float depth = (float) (1 / Math.pow(2, currDepth));
		boolean inSphere = true, anyInSphere = false;
		for(int xc = 0; xc < 2; xc++)
			for(int yc = 0; yc < 2; yc++)
				for(int zc = 0; zc < 2; zc++) {
					float xCoord = x + width * xc, yCoord = y + height * yc, zCoord = z + depth * zc;
					boolean works = xCoord * xCoord + yCoord * yCoord + zCoord * zCoord <= 0.25;
					inSphere &= works;
					anyInSphere |= works;
				}
		if(!anyInSphere && currDepth != 0) {
			node.children = new Voxel[0];
			return;
		} else if(!inSphere) {
			node.children = new Voxel[8];
			currDepth++;
			width = (float) (1 / Math.pow(2, currDepth));
			height = (float) (1 / Math.pow(2, currDepth));
			depth = (float) (1 / Math.pow(2, currDepth));
			for(int xc = 0; xc < 2; xc++)
				for(int yc = 0; yc < 2; yc++)
					for(int zc = 0; zc < 2; zc++) {
						float tmpX = x + width * xc;
						float tmpY = y + height * yc;
						float tmpZ = z + depth * zc;
						boolean anyInSphere2 = false;
						for(int xc2 = 0; xc2 < 2; xc2++)
							for(int yc2 = 0; yc2 < 2; yc2++)
								for(int zc2 = 0; zc2 < 2; zc2++) {
									float xCoord = tmpX + width * xc2, yCoord = tmpY + height * yc2, zCoord = tmpZ + depth * zc2;
									boolean works = xCoord * xCoord + yCoord * yCoord + zCoord * zCoord <= 0.25;
									anyInSphere2 |= works;
								}
						if(anyInSphere2)
							node.children[xc * 4 + yc * 2 + zc] = new Voxel();
					}
			for(int xc = 0; xc < 2; xc++)
				for(int yc = 0; yc < 2; yc++)
					for(int zc = 0; zc < 2; zc++)
						if(xc * 4 + yc * 2 + zc < node.children.length && node.children[xc * 4 + yc * 2 + zc] != null)
							makeSphere(node.children[xc * 4 + yc * 2 + zc], maxDepth, currDepth, x + width * xc, y + height * yc, z + depth * zc);
		}
	}

	@SuppressWarnings("unused")
	private void makePacman(Voxel node, int maxDepth, int currDepth, float x, float y, float z) {
		makeMovingPacman(node, maxDepth, currDepth, x, y, z, 0);
	}

	@SuppressWarnings("unused")
	private void makeMovingPacman(Voxel node, int maxDepth, int currDepth, float x, float y, float z, long time) {
		if(maxDepth == currDepth) {
			node.children = new Voxel[0];
			return;
		}
		double scale = Math.abs(Math.cos(time / 1000.0 * Math.PI / 5));
		float size = 1.0f / (1 << currDepth);
		boolean inSphere = true, anyInSphere = false;
		for(int count = 0; count < 8; count++) {
			float xCoord = x + size * (count / 4);
			float yCoord = y + size * ((count / 2) % 2);
			float zCoord = z + size * (count % 2);
			boolean works = xCoord * xCoord + yCoord * yCoord + zCoord * zCoord <= 0.25 && (xCoord <= 0.0 || yCoord >= xCoord * scale || yCoord <= -xCoord * scale);
			inSphere &= works;
			anyInSphere |= works;
		}
		if(!anyInSphere && currDepth != 0) {
			node.children = new Voxel[0];
		} else if(!inSphere) {
			node.children = new Voxel[8];
			currDepth++;
			size /= 2;	
			for(int count = 0; count < 8; count++) {
				float tmpX = x + size * (count / 4);
				float tmpY = y + size * ((count / 2) % 2);
				float tmpZ = z + size * (count % 2);
				boolean anyInSphere2 = false;
				for(int count2 = 0; count2 < 8; count2++) {
					float xCoord = tmpX + size * (count2 / 4), yCoord = tmpY + size * ((count2 / 2) % 2), zCoord = tmpZ + size * (count2 % 2);
					anyInSphere2 |= xCoord * xCoord + yCoord * yCoord + zCoord * zCoord <= 0.25 && (xCoord <= 0.0 || yCoord >= xCoord * scale || yCoord <= -xCoord * scale);
				}
				if(anyInSphere2)
					node.children[count] = new Voxel();
			}
			for (int count = 0; count < 8; count++)
				if (node.children[count] != null)
					makeMovingPacman(node.children[count], maxDepth, currDepth,
							(x + size * (count / 4)),
							(y + size * ((count / 2) % 2)),
							(z + size * (count % 2)), time);
		}
	}

	@SuppressWarnings("unused")
	private void makePacman(Pixel node, int maxDepth, int currDepth, float x, float y) {
		makeMovingPacman(node, maxDepth, currDepth, x, y, 0);
	}

	@SuppressWarnings("unused")
	private void makeMovingPacman(Pixel node, int maxDepth, int currDepth, float x, float y, long time) {
		if(maxDepth == currDepth) {
			node.children = new Pixel[0];
			return;
		}
		double scale = Math.abs(Math.cos(time / 1000.0 * Math.PI / 5));
		float width = (float) (1 / Math.pow(2, currDepth));
		float height = (float) (1 / Math.pow(2, currDepth));
		boolean inPac = true, anyInPac = false;
		for(int xc = 0; xc < 2; xc++)
			for(int yc = 0; yc < 2; yc++) {
				float xCoord = x + width * xc, yCoord = y + height * yc;
				boolean works = xCoord * xCoord + yCoord * yCoord <= 0.25 && (xCoord <= 0.0 || yCoord >= xCoord * scale || yCoord <= -xCoord * scale);
				inPac &= works;
				anyInPac |= works;
			}
		/*if(!anyInPac && currDepth != 0) {
			node.children = new Pixel[0];
		} else */if(!inPac) {
			node.children = new Pixel[4];
			currDepth++;
			
			width = (float) (1 / Math.pow(2, currDepth));
			height = (float) (1 / Math.pow(2, currDepth));
					
			for(int xc = 0; xc < 2; xc++)
				for(int yc = 0; yc < 2; yc++) {
//					float tmpX = x + width * xc;
//					float tmpY = y + height * yc;
//					boolean anyInSphere2 = false;
//					for(int xc2 = 0; xc2 < 2; xc2++)
//						for(int yc2 = 0; yc2 < 2; yc2++) {
//							float xCoord = tmpX + width * xc2, yCoord = tmpY + height * yc2;
//							boolean works = xCoord * xCoord + yCoord * yCoord <= 0.25 && (xCoord <= 0.0 || yCoord >= xCoord * scale || yCoord <= -xCoord * scale);
//							anyInSphere2 |= works;
//						}
//					if(anyInSphere2)
						node.children[2 * xc + yc] = new Pixel();
				}
			for(int xc = 0; xc < 2; xc++)
				for(int yc = 0; yc < 2; yc++)
					if(xc * 2 + yc < node.children.length && node.children[xc * 2 + yc] != null)
						makeMovingPacman(node.children[xc * 2 + yc], maxDepth, currDepth, x + width * xc, y + height * yc, time);
		}
	}

	@SuppressWarnings("unused")
	private void makeCircle(Pixel node, int maxDepth, int currDepth, float x, float y) {
		if(maxDepth == currDepth) {
			node.children = new Pixel[0];
			return;
		}
		float width = (float) (1 / Math.pow(2, currDepth));
		float height = (float) (1 / Math.pow(2, currDepth));
		boolean inPac = true, anyInPac = false;
		for(int xc = 0; xc < 2; xc++)
			for(int yc = 0; yc < 2; yc++) {
				float xCoord = x + width * xc, yCoord = y + height * yc;
				boolean works = xCoord * xCoord + yCoord * yCoord <= 0.25;
				inPac &= works;
				anyInPac |= works;
			}
		if(!anyInPac && currDepth != 0) {
			node.children = new Pixel[0];
		} else if(!inPac) {
			node.children = new Pixel[4];
			currDepth++;
			
			width = (float) (1 / Math.pow(2, currDepth));
			height = (float) (1 / Math.pow(2, currDepth));
					
			for(int xc = 0; xc < 2; xc++)
				for(int yc = 0; yc < 2; yc++) {
					float tmpX = x + width * xc;
					float tmpY = y + height * yc;
					boolean anyInSphere2 = false;
					for(int xc2 = 0; xc2 < 2; xc2++)
						for(int yc2 = 0; yc2 < 2; yc2++) {
							float xCoord = tmpX + width * xc2, yCoord = tmpY + height * yc2;
							boolean works = xCoord * xCoord + yCoord * yCoord <= 0.25;
							anyInSphere2 |= works;
						}
					if(anyInSphere2)
						node.children[2 * xc + yc] = new Pixel();
				}
			for(int xc = 0; xc < 2; xc++)
				for(int yc = 0; yc < 2; yc++)
					if(xc * 2 + yc < node.children.length && node.children[xc * 2 + yc] != null)
						makeCircle(node.children[xc * 2 + yc], maxDepth, currDepth, x + width * xc, y + height * yc);
		}
	}

	@SuppressWarnings("unused")
	private void makeGatling(Voxel node, int maxDepth, int currDepth, float x, float y, float z, int numChambers, float innerChamberDiam, float innerChamberWidth, float outerChamberDiam, float outerChamberWidth, float outerRingWidth) {
		if(maxDepth == currDepth) {
			node.children = new Voxel[0];
			return;
		}
		float width = (float) (1 / Math.pow(2, currDepth));
		float height = (float) (1 / Math.pow(2, currDepth));
		float depth = (float) (1 / Math.pow(2, currDepth));
		boolean inObject = true;
		for(int xc = 0; xc < 2; xc++)
			for(int yc = 0; yc < 2; yc++)
				for(int zc = 0; zc < 2; zc++) {
					float xCoord = x + width * xc, yCoord = y + height * yc, zCoord = z + depth * zc;
					float coordSquaredSum = xCoord * xCoord + yCoord * yCoord;
					float upperRad = innerChamberDiam / 2;
					float lowerRad = innerChamberDiam / 2 - innerChamberWidth;
					boolean works = coordSquaredSum <= upperRad * upperRad && coordSquaredSum >= lowerRad * lowerRad && zCoord >= -0.5f && zCoord <= 0.5f;
					inObject &= works;
				}
		if(!inObject) {
			float dist = innerChamberDiam + outerChamberDiam;
			for(int chamberCount = 0; chamberCount < numChambers; chamberCount++) {
				double angle = chamberCount * 2 * Math.PI / numChambers;
				float xOffset = (float) (Math.cos(angle) * dist) / 2;
				float yOffset = (float) (Math.sin(angle) * dist) / 2;
				boolean works = true;
				for(int xc = 0; xc < 2; xc++)
					for(int yc = 0; yc < 2; yc++)
						for(int zc = 0; zc < 2; zc++) {
							float xCoord = x - xOffset + width * xc, yCoord = y - yOffset + height * yc, zCoord = z + depth * zc;
							float coordSquaredSum = xCoord * xCoord + yCoord * yCoord;
							float upperRad = outerChamberDiam / 2;
							float lowerRad = outerChamberDiam / 2 - outerChamberWidth;
							works &= coordSquaredSum <= upperRad * upperRad && coordSquaredSum >= lowerRad * lowerRad && zCoord >= -0.5f && zCoord <= 0.5f;
						}
				if(works) {
					inObject = true;
					break;
				}
			}
		}
		if(!inObject) {
			boolean works = true;
			for(int xc = 0; xc < 2; xc++)
				for(int yc = 0; yc < 2; yc++)
					for(int zc = 0; zc < 2; zc++) {
						float xCoord = x + width * xc, yCoord = y + height * yc, zCoord = z + depth * zc;
						float coordSquaredSum = xCoord * xCoord + yCoord * yCoord;
						float upperRad = innerChamberDiam / 2  + outerChamberDiam + outerRingWidth;
						float lowerRad = innerChamberDiam / 2  + outerChamberDiam;
						works &= coordSquaredSum <= upperRad * upperRad && coordSquaredSum >= lowerRad * lowerRad && (zCoord >= -0.425f && zCoord <= -0.375f
								|| zCoord >= -0.325f && zCoord <= -0.275f || zCoord >= 0.275f && zCoord <= 0.325f || zCoord >= 0.375f && zCoord <= 0.425f);
					}
			inObject = works;
		}
		
		if(inObject) {
			node = new Voxel();
		} else {
			node.children = new Voxel[8];

			currDepth++;

			width = (float) (1 / Math.pow(2, currDepth));
			height = (float) (1 / Math.pow(2, currDepth));
			depth = (float) (1 / Math.pow(2, currDepth));
			
			for(int i = 0; i < node.children.length; i++)
				node.children[i] = new Voxel();
			for(int xc = 0; xc < 2; xc++)
				for(int yc = 0; yc < 2; yc++)
					for(int zc = 0; zc < 2; zc++)
						if(xc * 4 + yc * 2 + zc < node.children.length && node.children[xc * 4 + yc * 2 + zc] != null)
							makeGatling(node.children[xc * 4 + yc * 2 + zc], maxDepth, currDepth, x + width * xc, y + height * yc, z + depth * zc, numChambers, innerChamberDiam, innerChamberWidth, outerChamberDiam, outerChamberWidth, outerRingWidth);
		}
	}

	@SuppressWarnings("unused")
	private void makeDoubleHelix(Voxel node, int maxDepth, int currDepth, float x, float y, float z, float radius, float thickness) {
		if(maxDepth == currDepth) {
			node.children = new Voxel[0];
			return;
		}
		float width = (float) (1 / Math.pow(2, currDepth));
		float height = (float) (1 / Math.pow(2, currDepth));
		float depth = (float) (1 / Math.pow(2, currDepth));
		boolean inObject = true;
		for(int xc = 0; xc < 2; xc++)
			for(int yc = 0; yc < 2; yc++)
				for(int zc = 0; zc < 2; zc++) {
					float xCoord = x + width * xc, yCoord = y + height * yc, zCoord = z + depth * zc;
					float xOffset = (float) (Math.cos(4 * Math.PI * zCoord) / 4);
					float yOffset = (float) (Math.sin(4 * Math.PI * zCoord) / 4);
					xCoord = xCoord + xOffset;
					yCoord = yCoord + yOffset;
					float coordSquaredSum = xCoord * xCoord + yCoord * yCoord;
					double outerRadiusSquared = radius;
					outerRadiusSquared *= outerRadiusSquared;
					double innerRadiusSquared = radius - thickness;
					innerRadiusSquared *= innerRadiusSquared;
					boolean works = coordSquaredSum <= outerRadiusSquared && coordSquaredSum >= innerRadiusSquared && zCoord >= -0.5f && zCoord <= 0.5f;
					inObject &= works;
				}
		if(!inObject) {
			boolean works = true;
			for(int xc = 0; xc < 2; xc++)
				for(int yc = 0; yc < 2; yc++)
					for(int zc = 0; zc < 2; zc++) {
						float xCoord = x + width * xc, yCoord = y + height * yc, zCoord = z + depth * zc;
						float xOffset = (float) (Math.cos(4 * Math.PI * zCoord) / 4);
						float yOffset = (float) (Math.sin(4 * Math.PI * zCoord) / 4);
						xCoord = xCoord + xOffset;
						yCoord = yCoord + yOffset;
						float coordSquaredSum = xCoord * xCoord + yCoord * yCoord;
						double outerRadiusSquared = radius;
						outerRadiusSquared *= outerRadiusSquared;
						double innerRadiusSquared = radius - thickness;
						innerRadiusSquared *= innerRadiusSquared;
						works &= coordSquaredSum <= outerRadiusSquared && coordSquaredSum >= innerRadiusSquared && zCoord >= -0.5f && zCoord <= 0.5f;
					}
			inObject = works;
		}
		if(inObject) {
			node = new Voxel();
		} else {
			node.children = new Voxel[8];

			currDepth++;

			width = (float) (1 / Math.pow(2, currDepth));
			height = (float) (1 / Math.pow(2, currDepth));
			depth = (float) (1 / Math.pow(2, currDepth));
			
			for(int i = 0; i < node.children.length; i++)
				node.children[i] = new Voxel();
			for(int xc = 0; xc < 2; xc++)
				for(int yc = 0; yc < 2; yc++)
					for(int zc = 0; zc < 2; zc++)
						if(xc * 4 + yc * 2 + zc < node.children.length && node.children[xc * 4 + yc * 2 + zc] != null)
							makeDoubleHelix(node.children[xc * 4 + yc * 2 + zc], maxDepth, currDepth, x + width * xc, y + height * yc, z + depth * zc, radius, thickness);
		}
	}

	@SuppressWarnings("unused")
	private void makeFullSquare(Pixel node, int maxDepth, int currDepth) {
		if (node == null || maxDepth == currDepth)
			return;
		node.children = new Pixel[4];

		node.children[0] = new Pixel();
		node.children[1] = new Pixel();
		node.children[2] = new Pixel();
		node.children[3] = new Pixel();

		currDepth++;
		for (Pixel pix : node.children)
			makeFullSquare(pix, maxDepth, currDepth);
	}

	@SuppressWarnings("unused")
	private void makeFullCube(Voxel node, int maxDepth, int currDepth) {
		if (node == null || maxDepth == currDepth)
			return;
		node.children = new Voxel[8];
		
		node.children[0] = new Voxel();
		node.children[1] = new Voxel();
		node.children[2] = new Voxel();
		node.children[3] = new Voxel();
		node.children[4] = new Voxel();
		node.children[5] = new Voxel();
		node.children[6] = new Voxel();
		node.children[7] = new Voxel();

		currDepth++;
		for (Voxel vox : node.children)
			makeFullCube(vox, maxDepth, currDepth);
	}
}

class Voxel {
	Voxel[] children;

	void render(short x, short y, short z, byte maxDepth, byte treeDepth, short vertices[], byte depths[], int lengths[]) {
		short nodeSize = (short) (1 << (maxDepth - treeDepth));
		if (children == null) {
			vertices[lengths[0]++] = x;
			vertices[lengths[0]++] = y;
			vertices[lengths[0]++] = z;
			depths[lengths[1]++] = (byte) (maxDepth - treeDepth);
			return;
		}
		if(children.length == 0)
			return;
		treeDepth++;
		nodeSize >>= 1;
		short xw = (short) (x + nodeSize), yh = (short) (y + nodeSize), zd = (short) (z + nodeSize);
		if (children[0] != null) {
			children[0].render(x, y, z, maxDepth, treeDepth, vertices, depths, lengths);
			children[0] = null;
		}
		if (children[1] != null) {
			children[1].render(x, y, zd, maxDepth, treeDepth, vertices, depths, lengths);
			children[1] = null;
		}
		if (children[2] != null) {
			children[2].render(x, yh, z, maxDepth, treeDepth, vertices, depths, lengths);
			children[2] = null;
		}
		if (children[3] != null) {
			children[3].render(x, yh, zd, maxDepth, treeDepth, vertices, depths, lengths);
			children[3] = null;
		}
		if (children[4] != null) {
			children[4].render(xw, y, z, maxDepth, treeDepth, vertices, depths, lengths);
			children[4] = null;
		}
		if (children[5] != null) {
			children[5].render(xw, y, zd, maxDepth, treeDepth, vertices, depths, lengths);
			children[5] = null;
		}
		if (children[6] != null) {
			children[6].render(xw, yh, z, maxDepth, treeDepth, vertices, depths, lengths);
			children[6] = null;
		}
		if (children[7] != null) {
			children[7].render(xw, yh, zd, maxDepth, treeDepth, vertices, depths, lengths);
			children[7] = null;
		}
		children = null;
	}
}
	
class Pixel {
	Pixel[] children;

	void render(short x, short y, int maxDepth, int treeDepth, short vertices[], byte depths[], int lengths[]) {
		short nodeSize = (short) (1 << (maxDepth - treeDepth));
		if (children == null) {
			vertices[lengths[0]++] = x;
			vertices[lengths[0]++] = y;
			depths[lengths[1]++] = (byte) (maxDepth - treeDepth);
			return;
		}
		if (children.length == 0)
			return;
		treeDepth++;
		nodeSize >>= 1;
		short xw = (short) (x + nodeSize), yh = (short) (y + nodeSize);
		if (children[0] != null) {
			children[0].render(x, y, maxDepth, treeDepth, vertices, depths, lengths);
			children[0] = null;
		}
		if (children[1] != null) {
			children[1].render(x, yh, maxDepth, treeDepth, vertices, depths, lengths);
			children[1] = null;
		}
		if (children[2] != null) {
			children[2].render(xw, y, maxDepth, treeDepth, vertices, depths, lengths);
			children[2] = null;
		}
		if (children[3] != null) {
			children[3].render(xw, yh, maxDepth, treeDepth, vertices, depths, lengths);
			children[3] = null;
		}
		children = null;
	}
}

class Input extends GLFWKeyCallback {
	private static boolean[] pressed = new boolean[65536], repeated = new boolean[65536];

	public void invoke(long window, int key, int scancode, int action, int mods) {
		pressed[key] = action == GLFW_PRESS;
		repeated[key] = action == GLFW_REPEAT;
	}

	public static boolean isPressed(int key) {
		return pressed[key];
	}
	
	public static boolean isRepeated(int key) {
		return repeated[key];
	}
	
	public static boolean isDown(int key) {
		return isPressed(key) || isRepeated(key);
	}
}
