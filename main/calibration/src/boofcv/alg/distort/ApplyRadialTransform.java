/*
 * Copyright (c) 2011-2012, Peter Abeles. All Rights Reserved.
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

package boofcv.alg.distort;

import boofcv.struct.distort.PixelTransform_F32;
import georegression.geometry.GeometryMath_F32;
import georegression.struct.point.Point2D_F32;
import org.ejml.data.DenseMatrix64F;
import org.ejml.ops.CommonOps;

/**
 * Adds radial distortion to a distortion free pixel coordinates
 *
 * @author Peter Abeles
 */
public class ApplyRadialTransform extends PixelTransform_F32 {

	// principle point / image center
	private float x_c,y_c;
	// radial distortion
	private float kappa[];

	private DenseMatrix64F K_inv = new DenseMatrix64F(3,3);

	private Point2D_F32 temp0 = new Point2D_F32();
	private Point2D_F32 temp1 = new Point2D_F32();

	public ApplyRadialTransform(double fx, double fy, double skew, double x_c, double y_c, double[] kappa) {
		set(fx,fy,skew,x_c,y_c,kappa);
	}

	public ApplyRadialTransform() {
	}

	/**
	 * Specify camera calibration parameters
	 */
	public void set(double fx, double fy, double skew, double x_c, double y_c, double[] kappa) {

		K_inv.set(0,0,fx);
		K_inv.set(1,1,fy);
		K_inv.set(0,1,skew);
		K_inv.set(0,2,x_c);
		K_inv.set(1,2,y_c);
		K_inv.set(2,2,1);

		CommonOps.invert(K_inv);

		this.x_c = (float)x_c;
		this.y_c = (float)y_c;

		this.kappa = new float[kappa.length];
		for( int i = 0; i < kappa.length; i++ ) {
			this.kappa[i] = (float)kappa[i];
		}
	}
	
	@Override
	public void compute(int x, int y) {
		float sum = 0;

		temp0.x = x;
		temp0.y = y;
		
		GeometryMath_F32.mult(K_inv,temp0,temp1);

		float r2 = temp1.x*temp1.x + temp1.y*temp1.y;

		float r = r2;

		for( int i = 0; i < kappa.length; i++ ) {
			sum += kappa[i]*r;
			r *= r2;
		}

		distX = x + (x-x_c)*sum;
		distY = y + (y-y_c)*sum;
	}
}
