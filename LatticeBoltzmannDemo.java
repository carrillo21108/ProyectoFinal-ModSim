import java.awt.*;
import java.awt.event.*;
import java.awt.image.MemoryImageSource;
import java.text.DecimalFormat;

/*	A lattice-Boltzmann simulation in Java
*/

class LatticeBoltzmannDemo extends Canvas implements Runnable {

	// Global variables, starting with the grid size:
	int xdim = 200;				// dimensions of lattice
	int ydim = 80;
	int pixelsPerSquare = 3;	// for graphics
	
	// Here are the arrays of densities by velocity, named by velocity directions with north up:
	double[][] n0 = new double[xdim][ydim];
	double[][] nN = new double[xdim][ydim];
	double[][] nS = new double[xdim][ydim];
	double[][] nE = new double[xdim][ydim];
	double[][] nW = new double[xdim][ydim];
	double[][] nNW = new double[xdim][ydim];
	double[][] nNE = new double[xdim][ydim];
	double[][] nSW = new double[xdim][ydim];
	double[][] nSE = new double[xdim][ydim];
	
	// Other arrays calculated from the above:
	double[][] density = new double[xdim][ydim];		// total density
	double[][] xvel = new double[xdim][ydim];			// macroscopic x velocity
	double[][] yvel = new double[xdim][ydim];			// macroscopic y velocity
	double[][] speed2 = new double[xdim][ydim];			// macroscopic speed squared

	// Boolean array, true at sites that contain barriers:
	boolean[][] barrier = new boolean[xdim][ydim];

	int time = 0;	// time in units of the fundamental step size

	// Array of colors for graphics:
	int nColors = 600;

	int[] colorInt = new int[nColors];		// colors stored as integers for MemoryImageSource
	int blackColorInt = Color.HSBtoRGB((float)0,(float)1,(float)0);		// an integer to represent the color black
	{	for (int c=0; c<nColors; c++) {
			double h = Math.log(1 + c*1.0/nColors) / Math.log(2);  // Logarithmic spread
			h = (2.0/3) * h;
			colorInt[c] = Color.HSBtoRGB((float)h, (float)1, (float)1);
		}
	}
	
	int[] iPixels = new int[xdim * pixelsPerSquare * ydim * pixelsPerSquare];
	MemoryImageSource iSource = new MemoryImageSource(xdim*pixelsPerSquare,ydim*pixelsPerSquare,
																	iPixels,0,xdim*pixelsPerSquare);
	Image theImage;
	Image scaledImage;

	boolean running = false;	// true when the simulation thread is running
	int stepTime = 0;			// performance measure: time in ms for a single iteration of the algorithm
	int collideTime = 0;
	int streamTime = 0;
	int paintTime = 0;
	int mouseX, mouseY;		// mouse coordinates in grid units
	boolean mouseDrawBarrier = true;	// true when mouse is drawing rather than erasing a barrier
	
	Canvas dataCanvas;			// for numerical readouts
	DecimalFormat threePlaces = new DecimalFormat("0.000");
	Button runButton = new Button(" Run ");
	DoubleScroller viscScroller = new DoubleScroller("Viscosity = ",.01,1,.01,.02);
	DoubleScroller speedScroller = new DoubleScroller("Flow speed = ",0,0.12,0.005,0.1);
	DoubleScroller contrastScroller = new DoubleScroller("Contrast = ",1,100,1,20);

	// calculation short-cuts:
	double four9ths = 4.0 / 9;
	double one9th = 1.0 / 9;
	double one36th = 1.0 / 36;

	// Constructor method does all the initializations:
	LatticeBoltzmannDemo() {
	
		initFluid();	// initialize the fluid state
	
		// Start the GUI with a Frame and a Panel to hold the Canvas:
		setSize(xdim*pixelsPerSquare,ydim*pixelsPerSquare);
		Frame theFrame = new Frame("Simulacion Lattice-Boltzmann");
		theFrame.setResizable(false);
		theFrame.addWindowListener(new WindowAdapter() { 
			public void windowClosing(WindowEvent e) { 
				System.exit(0); 		// exit when user clicks close button
			} 
		});
		Panel canvasPanel = new Panel();
		theFrame.add(canvasPanel);
		canvasPanel.add(this);
		
		// Offscreen image and memory image source:
		iSource.setAnimated(true);
		theImage = createImage(iSource);
		
		// Add a control panel and data-readout canvas:
		Panel controlPanel = new Panel();
		theFrame.add(controlPanel,BorderLayout.SOUTH);
		controlPanel.setLayout(new GridLayout(0,1));	// divide controlPanel into equal-height rows
		dataCanvas = new Canvas() {
			public void paint(Graphics g) {
			}
		};
		controlPanel.add(dataCanvas);
		
		// Sub-panel for buttons:
		Panel cPanel1 = new Panel();
		controlPanel.add(cPanel1);
		cPanel1.add(runButton);
		runButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				running = !running;
				if (running) runButton.setLabel("Pause"); else runButton.setLabel("Run");
			}
		});
		Button resetButton = new Button("Reset fluid");
		cPanel1.add(resetButton);
		resetButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				initFluid();
				repaint();
			}
		});
		Button lineButton = new Button("Line");
		cPanel1.add(lineButton);
		lineButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				clearBarriers();
				makeLine(20);
			}
		});
		Button circleButton = new Button("Circle");
		cPanel1.add(circleButton);
		circleButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				clearBarriers();
				makeCircle(20);
			}
		});
		Button rectangleButton = new Button("Rectangle");
		cPanel1.add(rectangleButton);
		rectangleButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				clearBarriers();
				makeRectangle(20,20);
			}
		});
		Button triangleButton = new Button("Triangle");
		cPanel1.add(triangleButton);
		triangleButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				clearBarriers();
				makeTriangle(20);
			}
		});

		Button airfoilButton = new Button("Wing");
		cPanel1.add(airfoilButton);
		airfoilButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
						clearBarriers();
						makeAirfoil(xdim / 4, ydim / 2, 100, 12); // Posición y dimensiones del ala
				}
		});


		Button starButton = new Button("Star");
		cPanel1.add(starButton);
		starButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				clearBarriers();
				makeStar(20);
			}
		});
		Button clearButton = new Button("Clear barriers");
		cPanel1.add(clearButton);
		clearButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				clearBarriers();
			}
		});
		
		// Sub-panel for physical settings:
		Panel cPanel2 = new Panel();
		controlPanel.add(cPanel2);
		cPanel2.add(viscScroller);
		cPanel2.add(speedScroller);

		// GUI is complete so pack the frame and show it:
		theFrame.pack();
		theFrame.setVisible(true);
		
		makeLine(20);	// start with a linear barrier
		
		// Now start the simulation thread:
		Thread simThread = new Thread(this);
		simThread.start();
	}	// end of constructor method

	// Initialize the fluid with density 1 and user-chosen speed in x direction:
	synchronized void initFluid() {
		double v = speedScroller.getValue();
		for (int x=0; x<xdim; x++) {
			for (int y=0; y<ydim; y++) {
				if (barrier[x][y]) {
					zeroSite(x,y);
				} else {
					n0[x][y]  = four9ths * (1 - 1.5*v*v);
					nE[x][y]  =   one9th * (1 + 3*v + 3*v*v);
					nW[x][y]  =   one9th * (1 - 3*v + 3*v*v);
					nN[x][y]  =   one9th * (1 - 1.5*v*v);
					nS[x][y]  =   one9th * (1 - 1.5*v*v);
					nNE[x][y] =  one36th * (1 + 3*v + 3*v*v);
					nSE[x][y] =  one36th * (1 + 3*v + 3*v*v);
					nNW[x][y] =  one36th * (1 - 3*v + 3*v*v);
					nSW[x][y] =  one36th * (1 - 3*v + 3*v*v);
					density[x][y] = 1;
					xvel[x][y] = v;
					yvel[x][y] = 0;
					speed2[x][y] = v*v;
				}
			}
		}
		time = 0;	// reset time variable
	}

	// Clear all the user-drawn barriers:
	synchronized void clearBarriers() {
		for (int x=1; x<xdim-1; x++) {
			for (int y=1; y<ydim-1; y++) {
				if (barrier[x][y]) {
					barrier[x][y] = false;
					n0[x][y] = 1;
					density[x][y] = 1;
					speed2[x][y] = 0;
				}
			}
		}
	}

	// Create a linear barrier of a given length:
	synchronized void makeLine(int length) {
		mouseDrawBarrier = true;
		int x = ydim/2 - 1;
		for (int y=ydim/2-length/2-1; y<ydim/2-length/2+length-1; y++) {
			drawBarrier(x,y);
		}
	}

	synchronized void makeAirfoil(int centerX, int centerY, int length, int thickness) {
    mouseDrawBarrier = true;

    // Parámetros del perfil NACA
    double maxThickness = thickness / 100.0; // Grosor máximo como porcentaje del largo
    double camber = -0.04; // Curvatura máxima (inversión para el lado inferior)
    double camberPos = 0.4; // Posición de la curvatura máxima (40% del largo)

    for (int x = 0; x < length; x++) {
        // Coordenadas normalizadas
        double xNorm = (double) x / length;

        // Cálculo de la línea de curvatura (camber line)
        double yCamber;
        if (xNorm <= camberPos) {
            yCamber = camber / (camberPos * camberPos) * (2 * camberPos * xNorm - xNorm * xNorm);
        } else {
            yCamber = camber / ((1 - camberPos) * (1 - camberPos)) * ((1 - 2 * camberPos) + 2 * camberPos * xNorm - xNorm * xNorm);
        }

        // Grosor del perfil (distancia simétrica desde la línea de curvatura)
        double thicknessDist = 5 * maxThickness * (0.2969 * Math.sqrt(xNorm)
                - 0.1260 * xNorm
                - 0.3516 * xNorm * xNorm
                + 0.2843 * xNorm * xNorm * xNorm
                - 0.1015 * xNorm * xNorm * xNorm * xNorm);

        // Coordenadas del perfil superior e inferior
        int yUpper = (int) Math.round(centerY - (yCamber + thicknessDist) * length);
        int yLower = (int) Math.round(centerY - (yCamber - thicknessDist) * length);

        // Dibujar las barreras del ala
        drawBarrier(centerX + x, yUpper);
        drawBarrier(centerX + x, yLower);
    }
	}


	// Create a circular barrier of given diameter:
	synchronized void makeCircle(int diameter) {
		mouseDrawBarrier = true;
		double radius = (diameter-1)/2.0;		// 1->0, 2->.5, 3->1, 4->1.5, etc.
		double centerY = ydim/2 - 1;
		if (diameter % 2 == 0) centerY -= 0.5;	// shift down a bit if diameter is an even number
		double centerX = centerY;
		for (double theta=0; theta<2*Math.PI; theta+=0.1/radius) {
			int x = (int) Math.round(centerX + radius*Math.cos(theta));
			int y = (int) Math.round(centerY + radius*Math.sin(theta));
			drawBarrier(x,y);
			if (radius > 1) {
				x = (int) Math.round(centerX + (radius-0.5)*Math.cos(theta));
				y = (int) Math.round(centerY + (radius-0.5)*Math.sin(theta));
				drawBarrier(x,y);
			}
		}
	}

	synchronized void makeRectangle(int width, int height) {
		mouseDrawBarrier = true;
		int startX = ydim/2 - width/2;
		int startY = ydim/2 - height/2;
		
		// Draw horizontal lines
		for (int x = startX; x < startX + width; x++) {
			drawBarrier(x, startY);
			drawBarrier(x, startY + height - 1);
		}
		
		// Draw vertical lines
		for (int y = startY; y < startY + height; y++) {
			drawBarrier(startX, y);
			drawBarrier(startX + width - 1, y);
		}
	}

	synchronized void makeTriangle(int size) {
		mouseDrawBarrier = true;
		int centerX = ydim/2;
		int centerY = ydim/2;
		
		// Calculate triangle vertices
		int[] xPoints = {
			centerX,
			centerX - size/2,
			centerX + size/2
		};
		int[] yPoints = {
			centerY - size/2,
			centerY + size/2,
			centerY + size/2
		};
		
		// Draw lines between vertices
		for (int i = 0; i < 3; i++) {
			int nextIndex = (i + 1) % 3;
			drawBresenhamLine(xPoints[i], yPoints[i], xPoints[nextIndex], yPoints[nextIndex]);
		}
	}

	// Helper method for drawing lines using Bresenham's algorithm
	private void drawBresenhamLine(int x0, int y0, int x1, int y1) {
		int dx = Math.abs(x1 - x0);
		int dy = Math.abs(y1 - y0);
		int sx = x0 < x1 ? 1 : -1;
		int sy = y0 < y1 ? 1 : -1;
		int err = dx - dy;

		while (true) {
			drawBarrier(x0, y0);
			
			if (x0 == x1 && y0 == y1) break;
			
			int e2 = 2 * err;
			if (e2 > -dy) {
				err -= dy;
				x0 += sx;
			}
			if (e2 < dx) {
				err += dx;
				y0 += sy;
			}
		}
	}


	synchronized void makeStar(int size) {
		mouseDrawBarrier = true;
		int centerX = ydim/2;
		int centerY = ydim/2;

		int prevX = 0;
		int prevY = 0;
		
		for (int i = 0; i < 10; i++) {
			double angle = i * Math.PI / 5.0;
			double radius = (i % 2 == 0) ? size : size / 2.0;
			
			int x = (int) Math.round(centerX + radius * Math.cos(angle - Math.PI/2));
			int y = (int) Math.round(centerY + radius * Math.sin(angle - Math.PI/2));
			
			if (i > 0) {
				drawBresenhamLine(prevX, prevY, x, y);
			}
			
			prevX = x;
			prevY = y;
		}
		// Close the star
		drawBresenhamLine(prevX, prevY, 
			(int) Math.round(centerX + size * Math.cos(-Math.PI/2)), 
			(int) Math.round(centerY + size * Math.sin(-Math.PI/2))
		);
	}

	// Handy method to set all densities at a site to zero:
	void zeroSite(int x, int y) {
		n0[x][y] = 0;
		nE[x][y] = 0;
		nW[x][y] = 0;
		nN[x][y] = 0;
		nS[x][y] = 0;
		nNE[x][y] = 0;
		nNW[x][y] = 0;
		nSE[x][y] = 0;
		nSW[x][y] = 0;
		xvel[x][y] = 0;
		yvel[x][y] = 0;
		speed2[x][y] = 0;
	}

	// Run the simulation (called from separate thread):
	public void run() {
		while (true) {
			if (running) {
				for (int s=0; s<10; s++) doStep();
				try {Thread.sleep(1);} catch (InterruptedException e) {}
				repaint();
			} else {
				try {Thread.sleep(200);} catch (InterruptedException e) {}
			}
			repaint();	// repeated painting when not running uses resources but is handy for graphics adjustments
		}
	}

	// Execute a single step of the algorithm:
	// Times are on 3.06 GHz iMac, Java 6. On 2.4GHz MacBook Pro, all times are about 30% longer.
	synchronized void doStep() {
		long startTime = System.currentTimeMillis();
		//force();
		long forceTime = System.currentTimeMillis();
		collide();
		long afterCollideTime = System.currentTimeMillis();
		collideTime = (int) (afterCollideTime - forceTime);		// 23-24 ms for 600x600 grid
		stream();
		streamTime = (int) (System.currentTimeMillis() - afterCollideTime);	// 9-10 ms for 600x600 grid
		bounce();
		stepTime = (int) (System.currentTimeMillis() - startTime);	// 33-35 ms for 600x600 grid
		time++;
		dataCanvas.repaint();
	}

	// Collide particles within each cell.  Adapted from Wagner's D2Q9 code.
	void collide() {
		double n, one9thn, one36thn, vx, vy, vx2, vy2, vx3, vy3, vxvy2, v2, v215;
		double omega = 1 / (3*viscScroller.getValue() + 0.5);	// reciprocal of tau, the relaxation time
		for (int x=0; x<xdim; x++) {
			for (int y=0; y<ydim; y++) {
				if (!barrier[x][y]) {
					n = n0[x][y] + nN[x][y] + nS[x][y] + nE[x][y] + nW[x][y] + nNW[x][y] + nNE[x][y] + nSW[x][y] + nSE[x][y];
					density[x][y] = n;		// macroscopic density may be needed for plotting
					one9thn = one9th * n;
					one36thn = one36th * n;
					if (n > 0) {
						vx = (nE[x][y] + nNE[x][y] + nSE[x][y] - nW[x][y] - nNW[x][y] - nSW[x][y]) / n;
					} else vx = 0;
					xvel[x][y] = vx;		// may be needed for plotting
					if (n > 0) {
						vy = (nN[x][y] + nNE[x][y] + nNW[x][y] - nS[x][y] - nSE[x][y] - nSW[x][y]) / n;
					} else vy = 0;
					yvel[x][y] = vy;		// may be needed for plotting
					vx3 = 3 * vx;
					vy3 = 3 * vy;
					vx2 = vx * vx;
					vy2 = vy * vy;
					vxvy2 = 2 * vx * vy;
					v2 = vx2 + vy2;
					speed2[x][y] = v2;		// may be needed for plotting
					v215 = 1.5 * v2;
					n0[x][y]  += omega * (four9ths*n * (1                              - v215) - n0[x][y]);
					nE[x][y]  += omega * (   one9thn * (1 + vx3       + 4.5*vx2        - v215) - nE[x][y]);
					nW[x][y]  += omega * (   one9thn * (1 - vx3       + 4.5*vx2        - v215) - nW[x][y]);
					nN[x][y]  += omega * (   one9thn * (1 + vy3       + 4.5*vy2        - v215) - nN[x][y]);
					nS[x][y]  += omega * (   one9thn * (1 - vy3       + 4.5*vy2        - v215) - nS[x][y]);
					nNE[x][y] += omega * (  one36thn * (1 + vx3 + vy3 + 4.5*(v2+vxvy2) - v215) - nNE[x][y]);
					nNW[x][y] += omega * (  one36thn * (1 - vx3 + vy3 + 4.5*(v2-vxvy2) - v215) - nNW[x][y]);
					nSE[x][y] += omega * (  one36thn * (1 + vx3 - vy3 + 4.5*(v2-vxvy2) - v215) - nSE[x][y]);
					nSW[x][y] += omega * (  one36thn * (1 - vx3 - vy3 + 4.5*(v2+vxvy2) - v215) - nSW[x][y]);
				}
			}
		}
	}
	
	// Stream particles into neighboring cells:
	void stream() {
		for (int x=0; x<xdim-1; x++) {		// first start in NW corner...
			for (int y=ydim-1; y>0; y--) {
				nN[x][y] = nN[x][y-1];		// move the north-moving particles
				nNW[x][y] = nNW[x+1][y-1];	// and the northwest-moving particles
			}
		}
		for (int x=xdim-1; x>0; x--) {		// now start in NE corner...
			for (int y=ydim-1; y>0; y--) {
				nE[x][y] = nE[x-1][y];		// move the east-moving particles
				nNE[x][y] = nNE[x-1][y-1];	// and the northeast-moving particles
			}
		}
		for (int x=xdim-1; x>0; x--) {		// now start in SE corner...
			for (int y=0; y<ydim-1; y++) {
				nS[x][y] = nS[x][y+1];		// move the south-moving particles
				nSE[x][y] = nSE[x-1][y+1];	// and the southeast-moving particles
			}
		}
		for (int x=0; x<xdim-1; x++) {		// now start in the SW corner...
			for (int y=0; y<ydim-1; y++) {
				nW[x][y] = nW[x+1][y];		// move the west-moving particles
				nSW[x][y] = nSW[x+1][y+1];	// and the southwest-moving particles
			}
		}
		// We missed a few at the left and right edges:
		for (int y=0; y<ydim-1; y++) {
			nS[0][y] = nS[0][y+1];
		}
		for (int y=ydim-1; y>0; y--) {
			nN[xdim-1][y] = nN[xdim-1][y-1];
		}
		// Now handle left boundary as in Pullan's example code:
		// Stream particles in from the non-existent space to the left, with the
		// user-determined speed:
		double v = speedScroller.getValue();
		for (int y=0; y<ydim; y++) {
			if (!barrier[0][y]) {
				nE[0][y] = one9th * (1 + 3*v + 3*v*v);
				nNE[0][y] = one36th * (1 + 3*v + 3*v*v);
				nSE[0][y] = one36th * (1 + 3*v + 3*v*v);
			}
		}
		// Try the same thing at the right edge and see if it works:
		for (int y=0; y<ydim; y++) {
			if (!barrier[0][y]) {
				nW[xdim-1][y] = one9th * (1 - 3*v + 3*v*v);
				nNW[xdim-1][y] = one36th * (1 - 3*v + 3*v*v);
				nSW[xdim-1][y] = one36th * (1 - 3*v + 3*v*v);
			}
		}
		// Now handle top and bottom edges:
		for (int x=0; x<xdim; x++) {
			n0[x][0]  = four9ths * (1 - 1.5*v*v);
			nE[x][0]  =   one9th * (1 + 3*v + 3*v*v);
			nW[x][0]  =   one9th * (1 - 3*v + 3*v*v);
			nN[x][0]  =   one9th * (1 - 1.5*v*v);
			nS[x][0]  =   one9th * (1 - 1.5*v*v);
			nNE[x][0] =  one36th * (1 + 3*v + 3*v*v);
			nSE[x][0] =  one36th * (1 + 3*v + 3*v*v);
			nNW[x][0] =  one36th * (1 - 3*v + 3*v*v);
			nSW[x][0] =  one36th * (1 - 3*v + 3*v*v);
			n0[x][ydim-1]  = four9ths * (1 - 1.5*v*v);
			nE[x][ydim-1]  =   one9th * (1 + 3*v + 3*v*v);
			nW[x][ydim-1]  =   one9th * (1 - 3*v + 3*v*v);
			nN[x][ydim-1]  =   one9th * (1 - 1.5*v*v);
			nS[x][ydim-1]  =   one9th * (1 - 1.5*v*v);
			nNE[x][ydim-1] =  one36th * (1 + 3*v + 3*v*v);
			nSE[x][ydim-1] =  one36th * (1 + 3*v + 3*v*v);
			nNW[x][ydim-1] =  one36th * (1 - 3*v + 3*v*v);
			nSW[x][ydim-1] =  one36th * (1 - 3*v + 3*v*v);
		}
	}
	
	// Bounce particles off of barriers:
	// (The ifs are needed to prevent array index out of bounds errors. Could handle edges
	//  separately to avoid this.)
	void bounce() {
		for (int x=0; x<xdim; x++) {
			for (int y=0; y<ydim; y++) {
				if (barrier[x][y]) {
					if (nN[x][y] > 0) { nS[x][y-1] += nN[x][y]; nN[x][y] = 0; }
					if (nS[x][y] > 0) { nN[x][y+1] += nS[x][y]; nS[x][y] = 0; }
					if (nE[x][y] > 0) { nW[x-1][y] += nE[x][y]; nE[x][y] = 0; }
					if (nW[x][y] > 0) { nE[x+1][y] += nW[x][y]; nW[x][y] = 0; }
					if (nNW[x][y] > 0) { nSE[x+1][y-1] += nNW[x][y]; nNW[x][y] = 0; }
					if (nNE[x][y] > 0) { nSW[x-1][y-1] += nNE[x][y]; nNE[x][y] = 0; }
					if (nSW[x][y] > 0) { nNE[x+1][y+1] += nSW[x][y]; nSW[x][y] = 0; }
					if (nSE[x][y] > 0) { nNW[x-1][y+1] += nSE[x][y]; nSE[x][y] = 0; }
				}
			}
		}
	}

	// Compute the curl of the velocity field, paying special attention to edges:
	double[][] curl = new double[xdim][ydim];
	void computeCurl() {
		for (int x=1; x<xdim-1; x++) {
			for (int y=1; y<ydim-1; y++) {
				curl[x][y] = (yvel[x+1][y] - yvel[x-1][y]) - (xvel[x][y+1] - xvel[x][y-1]);
			}
		}
		for (int y=1; y<ydim-1; y++) {
			curl[0][y] = 2*(yvel[1][y] - yvel[0][y]) - (xvel[0][y+1] - xvel[0][y-1]);
			curl[xdim-1][y] = 2*(yvel[xdim-1][y] - yvel[xdim-2][y]) - (xvel[xdim-1][y+1] - xvel[xdim-1][y-1]);
		}
	}

	// Override update method to skip drawing background color:
	public void update(Graphics g) {
		paint(g);
	}

	// Paint method draws everything:
	public void paint(Graphics g) {
		long startTime = System.currentTimeMillis();
		computeCurl();
		double contrast = 20.0;	// multiplicative factor for colors
		int colorIndex;	// index into array of colors
		int theColor;	// color of a square, stored as an integer
		int pIndex = 0;	// index into pixel array
		for (int y=ydim-1; y>=0; y--) {		// note that we loop over y (row number) first, high to low
			for (int x=0; x<xdim; x++) {
				if (barrier[x][y]) {
					theColor = blackColorInt;
				} else {
					colorIndex = (int) (nColors * (0.5 + curl[x][y] * contrast * 0.3));

					if (colorIndex < 0) colorIndex = 0;
					if (colorIndex >= nColors) colorIndex = nColors - 1;
					theColor = colorInt[colorIndex];
				}
				// Now draw a square the hard way, one pixel at a time...
				// (We could make the memory image one pixel per square and use drawImage to enlarge it,
				//  but on Java 1.5 for Mac and perhaps others, this blurs the image.)
				for (int j=0; j<pixelsPerSquare; j++) {		// loop over rows of pixels
					for (int i=0; i<pixelsPerSquare; i++) {		// loop over columns of pixels
						iPixels[pIndex] = theColor;
						pIndex++;
					}
					pIndex += (xdim-1) * pixelsPerSquare;	// go to the next row
				}
				pIndex += pixelsPerSquare * (1 - xdim * pixelsPerSquare);	// get ready for next square
				//g.fillRect(x*pixelsPerSquare,(ydim-y-1)*pixelsPerSquare,pixelsPerSquare,pixelsPerSquare);
			}
			pIndex -= xdim * pixelsPerSquare * (1 - pixelsPerSquare);	// get ready for next grid row
		}
		iSource.newPixels(0,0,xdim*pixelsPerSquare,ydim*pixelsPerSquare);	// inform AWT that memory image has changed
		g.drawImage(theImage,0,0,null);		// blast the image to the screen
		
		paintTime = (int) (System.currentTimeMillis() - startTime);
	}	// end of paint method

	// A grid point has been clicked or dragged; create or erase a barrier accordingly:
	void drawBarrier(int x, int y) {
		if (mouseDrawBarrier) {
			barrier[x][y] = true;
			zeroSite(x,y);			// set all densities to zero if drawing a barrier here
		} else {
			if (barrier[x][y]) {	// don't erase unless there's actually a barrier here
				barrier[x][y] = false;
				n0[x][y] = 1;		// place some motionless fluid here with density 1
				density[x][y] = 1;
				speed2[x][y] = 0;	// paint method needs to know that speed is zero
			}
		}
		repaint();
	}

	// Boring main method to get things started:
	public static void main(String[] arg) {
		new LatticeBoltzmannDemo();
	}
}	// end of class LatticeBoltzmannDemo