
package gui;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

import java.nio.*;
import java.util.*;

import org.lwjgl.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

public class LWJGLExample implements Runnable {
	private long window;

	private Thread thread;
	private boolean running = false;

	private String title = "octree-example";

	private boolean resizable = false;
	private int msaaSamples = 4;

	private int width = 1000;
	private int height = 1000;

	private boolean showFramerate = true;
	long frameTimer;
	int frames;

	private double rotate_x = 0;
	private double rotate_y = 0;
	private double rotate_z = 0;
	
	private int maxDepth = 10;
	private boolean voxel = true;

	public void start() {
		running = true;
		thread = new Thread(this, title);
		thread.start();
	}

	public void run() {
		init();
		
		Voxel voxelRoot = new Voxel();
//		makeFullCube(voxelRoot, maxDepth, 0);
//		makeSphere(voxelRoot, maxDepth, 0, -0.5f, -0.5f, -0.5f);
		makePacman(voxelRoot, maxDepth, 0, -0.5f, -0.5f, -0.5f);
//		makeGatling(voxelRoot, maxDepth, 0, -0.5f, -0.5f, -0.5f, 9, 0.3f, 0.03f, 0.15f, 0.03f, 0.03f);
//		makeDoubleHelix(voxelRoot, maxDepth, 0, -0.5f, -0.5f, -0.5f, 0.1f, 0.1f);
		
		Pixel pixelRoot = new Pixel();
//		makeFullSquare(pixelRoot, maxDepth, 0);
//		makeCircle(pixelRoot, maxDepth, 0, -0.5f, -0.5f);
//		makePacman(pixelRoot, maxDepth, 0, -0.5f, -0.5f);
		
		int size = voxel ? voxelRoot.size() : pixelRoot.size();
		long quads = voxel ? size * 6l : size;
		
		System.out.println("Voxels: " + size);
		System.out.println("Quads: " + quads);
		
		int numVertexCoords = size * (voxel ? 24 : 4) * (voxel ? 3 : 2);
		int numColorCoords = size * (voxel ? 24 : 4) * 3;
		short[] vertices = new short[numVertexCoords];
		if(voxel)
			voxelRoot.render((short) -(1 << (maxDepth - 1)), (short) -(1 << (maxDepth - 1)), (short) -(1 << (maxDepth - 1)), maxDepth, 0, new int[] { 0 }, vertices);
		else
			pixelRoot.render((short) -(1 << (maxDepth - 1)), (short) -(1 << (maxDepth - 1)), maxDepth, 0, new int[] { 0 }, vertices);
		
		ByteBuffer interleavedBuffer = BufferUtils.createByteBuffer(numVertexCoords * 2 + numColorCoords);
		int colorMax = 1 << Byte.SIZE, vertexOffset = 0;
		Random rand = new Random(12345);
		for(int quadInd = 0; quadInd < vertices.length / (voxel ? 3 : 2); quadInd += 4) {
			Vertex vert;
			byte r = (byte) rand.nextInt(colorMax);
			byte g = (byte) rand.nextInt(colorMax);
			byte b = (byte) rand.nextInt(colorMax);
			for(byte count = 0; count < 4; count++) {
				if(voxel)
					vert = new Vertex(vertices[vertexOffset++], vertices[vertexOffset++], vertices[vertexOffset++], r, g, b);
				else
					vert = new Vertex(vertices[vertexOffset++], vertices[vertexOffset++], r, g, b);
				short[] vertexCoords = vert.getVertexCoords();
				byte[] colorValues = vert.getColors();
				interleavedBuffer.putShort(vertexCoords[0]);
				interleavedBuffer.putShort(vertexCoords[1]);
				if(voxel)
					interleavedBuffer.putShort(vertexCoords[2]);
				interleavedBuffer.put(colorValues);
			}
		}
		interleavedBuffer.flip();
		
		glEnable(GL_MULTISAMPLE);

//		 float[] lightPos = { 1.0f, 1.0f, 1.0f, 0.0f };
//		 float[] lightColor = { 1.0f, 0.0f, 1.0f, 1.0f };
//		 glLightfv(GL_LIGHT0, GL_POSITION,
//				 (FloatBuffer) BufferUtils.createFloatBuffer(lightPos.length).put(lightPos).flip());
//		 glLightfv(GL_LIGHT0, GL_AMBIENT,
//				 (FloatBuffer) BufferUtils.createFloatBuffer(lightPos.length).put(lightColor).flip());
//		 glEnable(GL_LIGHTING);
//		 glEnable(GL_LIGHT0);
		
		int vboID = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, vboID);
		glBufferData(GL_ARRAY_BUFFER, interleavedBuffer, GL_STATIC_DRAW);
		glEnable(GL_VERTEX_ARRAY);
		glEnable(GL_COLOR_ARRAY);
		glBindBuffer(GL_ARRAY_BUFFER, vboID);
		glVertexPointer(voxel ? 3 : 2, GL_SHORT, voxel ? 9 : 7, 0);
		glColorPointer(3, GL_UNSIGNED_BYTE, voxel ? 9 : 7, voxel ? 6 : 4);
		
		frameTimer = System.currentTimeMillis();
		
		while (running) {
			update();
			render(size * (voxel ? 24 : 4), (voxel ? 0.5 : 1.0) / (1 << (maxDepth - 1)));
			printFramerate();
			if (glfwWindowShouldClose(window))
				running = false;
		}

		glBindBuffer(GL_ARRAY_BUFFER, 0);
		glBindVertexArray(0);
		glDisableClientState(GL_VERTEX_ARRAY);
		glDisableClientState(GL_COLOR_ARRAY);

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
		glfwWindowHint(GLFW_SAMPLES, msaaSamples); // the window will be resizable

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
		
		glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
	}

	private void render(int numVertices, double scale) {
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // clear the framebuffer
		glLoadIdentity();

		glRotated(rotate_x, 1.0, 0.0, 0.0);
		glRotated(rotate_y, 0.0, 1.0, 0.0);
		glRotated(rotate_z, 0.0, 0.0, 1.0);
		glScaled(scale, scale, scale);

		glDrawArrays(GL_QUADS, 0, numVertices);

		glFlush();
		glfwSwapBuffers(window);
	}

	private void update() {
		glfwPollEvents();
		if (Input.isPressed(GLFW_KEY_ESCAPE))
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
		if(maxDepth == currDepth) {
			node.children = new Voxel[0];
			return;
		}
		float size = 1.0f / (1 << currDepth);
		boolean inSphere = true, anyInSphere = false;
		for(int count = 0; count < 8; count++) {
			float xCoord = x + size * (count / 4);
			float yCoord = y + size * ((count / 2) % 2);
			float zCoord = z + size * (count % 2);
			boolean works = xCoord * xCoord + yCoord * yCoord + zCoord * zCoord <= 0.25 && zCoord >= 0 && (xCoord <= 0.0 || yCoord >= xCoord || yCoord <= -xCoord);
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
					anyInSphere2 |= xCoord * xCoord + yCoord * yCoord + zCoord * zCoord <= 0.25 && zCoord >= 0 && (xCoord <= 0.0 || yCoord >= xCoord || yCoord <= -xCoord);
				}
				if(anyInSphere2)
					node.children[count] = new Voxel();
			}
			for (int count = 0; count < 8; count++)
				if (node.children[count] != null)
					makePacman(node.children[count], maxDepth, currDepth,
							(x + size * (count / 4)),
							(y + size * ((count / 2) % 2)),
							(z + size * (count % 2)));
		}
	}

	@SuppressWarnings("unused")
	private void makePacman(Pixel node, int maxDepth, int currDepth, float x, float y) {
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
				boolean works = xCoord * xCoord + yCoord * yCoord <= 0.25 && (xCoord <= 0.0 || yCoord >= xCoord || yCoord <= -xCoord);
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
							boolean works = xCoord * xCoord + yCoord * yCoord <= 0.25 && (xCoord <= 0.0 || yCoord >= xCoord || yCoord <= -xCoord);
							anyInSphere2 |= works;
						}
					if(anyInSphere2)
						node.children[2 * xc + yc] = new Pixel();
				}
			for(int xc = 0; xc < 2; xc++)
				for(int yc = 0; yc < 2; yc++)
					if(xc * 2 + yc < node.children.length && node.children[xc * 2 + yc] != null)
						makePacman(node.children[xc * 2 + yc], maxDepth, currDepth, x + width * xc, y + height * yc);
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

	void render(short x, short y, short z, int maxDepth, int treeDepth, int[] num, short[] vertices) {
		short size = (short) (1 << (maxDepth - treeDepth));
		if (children == null) {
			short xw = (short) (x + size), yh = (short) (y + size), zd = (short) (z + size);
			// back face
			vertices[num[0]++] = x;
			vertices[num[0]++] = y;
			vertices[num[0]++] = zd;
			vertices[num[0]++] = xw;
			vertices[num[0]++] = y;
			vertices[num[0]++] = zd;
			vertices[num[0]++] = xw;
			vertices[num[0]++] = yh;
			vertices[num[0]++] = zd;
			vertices[num[0]++] = x;
			vertices[num[0]++] = yh;
			vertices[num[0]++] = zd;
			
			// front face
			vertices[num[0]++] = x;
			vertices[num[0]++] = y;
			vertices[num[0]++] = z;
			vertices[num[0]++] = xw;
			vertices[num[0]++] = y;
			vertices[num[0]++] = z;
			vertices[num[0]++] = xw;
			vertices[num[0]++] = yh;
			vertices[num[0]++] = z;
			vertices[num[0]++] = x;
			vertices[num[0]++] = yh;
			vertices[num[0]++] = z;
			
			// bottom face
			vertices[num[0]++] = x;
			vertices[num[0]++] = y;
			vertices[num[0]++] = z;
			vertices[num[0]++] = xw;
			vertices[num[0]++] = y;
			vertices[num[0]++] = z;
			vertices[num[0]++] = xw;
			vertices[num[0]++] = y;
			vertices[num[0]++] = zd;
			vertices[num[0]++] = x;
			vertices[num[0]++] = y;
			vertices[num[0]++] = zd;
			
			// top face
			vertices[num[0]++] = x;
			vertices[num[0]++] = yh;
			vertices[num[0]++] = z;
			vertices[num[0]++] = xw;
			vertices[num[0]++] = yh;
			vertices[num[0]++] = z;
			vertices[num[0]++] = xw;
			vertices[num[0]++] = yh;
			vertices[num[0]++] = zd;
			vertices[num[0]++] = x;
			vertices[num[0]++] = yh;
			vertices[num[0]++] = zd;
			
			// left face
			vertices[num[0]++] = x;
			vertices[num[0]++] = y;
			vertices[num[0]++] = z;
			vertices[num[0]++] = x;
			vertices[num[0]++] = y;
			vertices[num[0]++] = zd;
			vertices[num[0]++] = x;
			vertices[num[0]++] = yh;
			vertices[num[0]++] = zd;
			vertices[num[0]++] = x;
			vertices[num[0]++] = yh;
			vertices[num[0]++] = z;

			// left face
			vertices[num[0]++] = xw;
			vertices[num[0]++] = y;
			vertices[num[0]++] = z;
			vertices[num[0]++] = xw;
			vertices[num[0]++] = y;
			vertices[num[0]++] = zd;
			vertices[num[0]++] = xw;
			vertices[num[0]++] = yh;
			vertices[num[0]++] = zd;
			vertices[num[0]++] = xw;
			vertices[num[0]++] = yh;
			vertices[num[0]++] = z;
			return;
		}
		if(children.length == 0)
			return;
		treeDepth++;
		size >>= 1;
		for (int count = 0; count < 8; count++)
			if (children[count] != null)
				children[count].render((short) (x + size * (count / 4)),
						(short) (y + size * ((count / 2) % 2)),
						(short) (z + size * (count % 2)),
						maxDepth, treeDepth, num, vertices);
	}

	int size() {
		if(children == null)
			return 1;
		int sum = 0;
		for(Voxel vox : children)
			if(vox != null)
				sum += vox.size();
		return sum;
	}
}
	
class Pixel {
	Pixel[] children;

	void render(short x, short y, int maxDepth, int treeDepth, int[] num, short[] vertices) {
		short size = (short) (1 << (maxDepth - treeDepth));
		if (children == null) {
			short xw = (short) (x + size), yh = (short) (y + size);
			vertices[num[0]++] = x;
			vertices[num[0]++] = y;
			vertices[num[0]++] = xw;
			vertices[num[0]++] = y;
			vertices[num[0]++] = xw;
			vertices[num[0]++] = yh;
			vertices[num[0]++] = x;
			vertices[num[0]++] = yh;
			return;
		}
		treeDepth++;
		size >>= 1;
		byte min = (byte) Math.min(4, children.length);
		for (byte count = 0; count < min; count++)
			if (children[count] != null)
				children[count].render((short) (x + size * (count / 2)),
						(short) (y + size * (count % 2)),
						maxDepth, treeDepth, num, vertices);
	}

	int size() {
		if (children == null)
			return 1;
		int sum = 0;
		for(Pixel pix : children)
			if(pix != null)
				sum += pix.size();
		return sum;
	}
}

class Vertex {
	private short[] vertexCoords;
	private byte[] colors;

	public Vertex(short x, short y, short z) {
		this(x, y, z, (byte) (Math.random() * (1 << 8)), (byte) (Math.random() * (1 << 8)), (byte) (Math.random() * (1 << 8)));
	}
	
	public Vertex(short x, short y, short z, byte r, byte g, byte b) {
		vertexCoords = new short[] {x, y, z};
		colors = new byte[] {r, g, b};
	}

	public Vertex(short x, short y) {
		this(x, y, (byte) (Math.random() * (1 << 8)), (byte) (Math.random() * (1 << 8)), (byte) (Math.random() * (1 << 8)));
	}
	
	public Vertex(short x, short y, byte r, byte g, byte b) {
		vertexCoords = new short[] {x, y};
		colors = new byte[] {r, g, b};
	}
	
	public short[] getVertexCoords() {
		return vertexCoords;
	}
	
	public byte[] getColors() {
		return colors;
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
