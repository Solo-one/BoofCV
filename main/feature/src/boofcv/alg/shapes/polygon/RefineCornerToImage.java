/*
 * Copyright (c) 2011-2015, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package boofcv.alg.shapes.polygon;

import boofcv.alg.shapes.edge.SnapToEdge;
import boofcv.struct.distort.PixelTransform_F32;
import boofcv.struct.image.ImageSingleBand;
import georegression.metric.Intersection2D_F64;
import georegression.struct.line.LineGeneral2D_F64;
import georegression.struct.point.Point2D_F64;

/**
 * <p>
 * Refines the estimate of a corner.  A corner is defined as the intersection of two straight edges.  The inside
 * edge is either all darker or lighter than the background.  The optimization function when refining the edge
 * seeks to maximize the difference between the inside and outside of the edge.  Internally {@link SnapToEdge} is
 * used to perform this optimization.
 * </p>
 *
 * <p> The corner is defined using three points.  One corner and two line end points.  One is to the left of the right
 * line.  Left is defined as counter-clockwise.  Optimization is done by fitting a line with that initial seed.  It
 * is then iteratily updated by finding new end points with the new line that are the same distance away.  Pixels
 * near the corner are excluded from the optimization because the edge is less clear at that point.
 * </p>
 *
 * <p>For input polygons which are in undistorted coordinates by with a distorted image call {@link #getSnapToEdge()}
 * and invoke {@link SnapToEdge#setTransform(int, int, PixelTransform_F32)}.</p>
 *
 * @author Peter Abeles
 */
public class RefineCornerToImage<T extends ImageSingleBand> {

	// How far away from a corner will it sample the line
	double cornerOffset;

	// maximum number of points it will sample along the line
	int maxLineSamples;

	// maximum number of iterations
	private int maxIterations;

	// convergence tolerance in pixels
	private double convergeTolPixels;

	// determines if it's fitting to a darker or lighter polygon
	private boolean fitBlack = true;

	// fits the line to the edge
	private SnapToEdge<T> snapToEdge;

	//---------- storage for local work space
	// adjusted corner points which have been offset from the true corners
	private Point2D_F64 adjA = new Point2D_F64();

	// storage for the previous corner and the refined corner
	Point2D_F64 previous = new Point2D_F64();
	Point2D_F64 refined = new Point2D_F64();
	// line end point workspace
	Point2D_F64 _endLeft = new Point2D_F64();
	Point2D_F64 _endRight = new Point2D_F64();


	// storage for fitted lines
	LineGeneral2D_F64 lineLeft = new LineGeneral2D_F64();
	LineGeneral2D_F64 lineRight = new LineGeneral2D_F64();

	// the input image
	protected T image;
	Class<T> imageType;

	/**
	 * Constructor which provides full access to all parameters.  See code documents
	 * value a description of these variables.
	 *
	 */
	public RefineCornerToImage(double cornerOffset, int maxLineSamples, int sampleRadius,
							   int maxIterations, double convergeTolPixels, boolean fitBlack,
							   Class<T> imageType) {
		if( sampleRadius < 1 )
			throw new IllegalArgumentException("Sample radius must be >= 1 to work");

		this.cornerOffset = cornerOffset;
		this.maxIterations = maxIterations;
		this.convergeTolPixels = convergeTolPixels;
		this.snapToEdge = new SnapToEdge<T>(maxLineSamples,sampleRadius,imageType);
		this.maxLineSamples = maxLineSamples;
		this.fitBlack = fitBlack;
		this.imageType = imageType;
	}

	/**
	 * Simplified constructor which uses reasonable default values for most variables
	 * @param fitBlack If true it's fitting a black square with a white background.  false is the inverse.
	 * @param imageType Type of input image it processes
	 */
	public RefineCornerToImage(boolean fitBlack, Class<T> imageType) {
		this(2.0, 10, 2, 10, 0.01, fitBlack, imageType);
	}

	/**
	 * Sets the image which is going to be processed.  If a transform is to be used
	 * {@link SnapToEdge#setTransform} should be called before this.
	 */
	public void initialize(T image) {
		this.image = image;
		this.snapToEdge.setImage(image);
	}

	/**
	 * Refines the fit a polygon by snapping it to the edges.
	 *
	 * @param corner (input) Initial estimate of polygon corner. Not modified.
	 * @param endLeft (input) End point of left line.  Not modified.
	 * @param endRight (input) End point of right line. Not modified.
	 */
	public boolean refine( Point2D_F64 corner , Point2D_F64 endLeft , Point2D_F64 endRight )
	{
		// local copy to avoid modifying the input
		_endLeft.set(endLeft);
		_endRight.set(endRight);

		double lengthLeft = corner.distance(endLeft);
		double lengthRight = corner.distance(endRight);

		// if the lines are too small exit
		if( lengthLeft < 2*cornerOffset || lengthRight < 2*cornerOffset)
			return false;

		// compute the number of points it will actually sample.  Doing too many is pointless
		int samplesLeft = Math.min(maxLineSamples,(int)Math.ceil(lengthLeft));
		int samplesRight = Math.min(maxLineSamples,(int)Math.ceil(lengthRight));

		refined.set(corner);
		previous.set(corner);

		// pixels squares is faster to compute
		double convergeTol = convergeTolPixels*convergeTolPixels;

		for (int iteration = 0; iteration < maxIterations; iteration++) {

			snapToEdge.setLineSamples(samplesLeft);
			if( !optimize(refined, _endLeft, lineLeft, fitBlack) )
				return false;

			snapToEdge.setLineSamples(samplesRight);
			if( !optimize(refined,_endRight, lineRight, !fitBlack) )
				return false;

			// intersect the two lines to fine the new corner
			if( null == Intersection2D_F64.intersection(lineLeft,lineRight,refined) )
				return false;

			// see if it has converged
			if( refined.distance2(previous) < convergeTol ) {
				break;
			}

			// find new line end points
			lineLeft.normalize();
			_endLeft.x = refined.x + lineLeft.B*lengthLeft;
			_endLeft.y = refined.y - lineLeft.A*lengthLeft;

			lineRight.normalize();
			_endRight.x = refined.x + lineRight.B*lengthRight;
			_endRight.y = refined.y - lineRight.A*lengthRight;

			// save the current corner
			previous.set(refined);
		}

		return true;
	}

	/**
	 * Fits a line defined by the two points. When fitting the line the weight of the edge is used to determine
	 * how influential the point is
	 * @param a Corner point in image coordinates.
	 * @param b Corner point in image coordinates.
	 * @param found (output) Line in image coordinates
	 * @return true if successful or false if it failed
	 */
	protected boolean optimize( Point2D_F64 a , Point2D_F64 b , LineGeneral2D_F64 found, boolean darkRight ) {

		double slopeX = (b.x - a.x);
		double slopeY = (b.y - a.y);
		double r = Math.sqrt(slopeX*slopeX + slopeY*slopeY);
		// vector of unit length pointing in direction of the slope
		double unitX = slopeX/r;
		double unitY = slopeY/r;

		// offset from corner because the gradient because unstable around there
		adjA.x = a.x + unitX*cornerOffset;
		adjA.y = a.y + unitY*cornerOffset;

		if( darkRight )
			return snapToEdge.refine(adjA,b,found);
		else
			return snapToEdge.refine(b,adjA,found);
	}

	public Point2D_F64 getRefinedCorner() {
		return refined;
	}

	public Point2D_F64 getRefinedEndLeft() {
		return _endLeft;
	}

	public Point2D_F64 getRefinedEndRight() {
		return _endRight;
	}

	public SnapToEdge<T> getSnapToEdge() {
		return snapToEdge;
	}

	/**
	 * True if it is fitting black to the image
	 */
	public boolean isFitBlack() {
		return fitBlack ;
	}
}
