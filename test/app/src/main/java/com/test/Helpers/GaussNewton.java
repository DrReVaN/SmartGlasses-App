package com.test.Helpers;

import android.os.Build;

import java.util.stream.IntStream;

abstract class GaussNewton {

    private static final double alpha = 1e-6;

    /**
     * Initialises the parameters and trigger the main optimisation function.
     * @param x The independent observed values
     * @param y The dependent values observed
     * @param numberOfParameters The number of unknown parameters in y function
     * @return optimised values of parameters in y function
     * @throws NoSquareException
     */
    public double[] optimise(double[][] x, double[] y, int numberOfParameters) throws NoSquareException {
        double[] b = new double[numberOfParameters];
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            IntStream.range(0, b.length).forEach(i -> b[i] = 1.);
        }
        return optimise(x, y, b);
    }

    /**
     * Implementation of Gauss-Newton optimisation algorithm.
     * Minimises the difference between actual y and the predicted y by changing the parameters in y function.
     * In each iteration:
     * 1. accepts an initial values for all parameters in y function in the b matrix
     * 2. Using the parameter matrix b, it predicts the function (y') and calculates residules as y - y';
     * 3. Calculates Jacobian matrix J
     * 4. Calculates (JT * J)^-1 * JT with JT as transpose of J
     * 5. Multiplies the matrix in step 4 to resisule matrix in step 2: (JT * J)^-1 * JT * res
     * 6. The results of step 5 is a new row matrix that is used to calculate new values for b matrix;
     *     new_b = old_b -  (JT * J)^-1 * JT * res
     * 7. However, depending on the initial values for b matrix, there is a chance that the optimisation will never, converge. This
     * happens when the initial b matrix is far from the optimum values. To remedy this, we multiply the second term in the above equation
     * by a small fraction. The downside of applying this fraction is that the number of iterations required for this optimisation will
     * increase:
     *     new_b = old_b -  gamma * (JT * J)^-1 * JT * res
     * We have chosen a value of 0.01 for gamma which seems to be working with any initial values for b.
     * 8. Continue the above steps in other iterations in order to reach the required precision.
     *
     * @param x The independent observed values
     * @param y The dependent values observed
     * @param b The parameter matrix
     * @return The optimised values of parameters in y function
     * @throws NoSquareException
     */
    public double[] optimise(double[][] x, double[] y, double[] b) throws NoSquareException {
        int maxIteration = 200;
        double oldError = 100;
        double precision = 1e-6;
        double[] b2 = b.clone();
        double gamma = .01;
        for (int i = 0; i < maxIteration; i++) {
            double[][] res = calculateResiduals(x, y, b2);
            double error = calculateError(res);
            //System.out.println("Iteration : " + i + ", Error-diff: " + (Math.abs(oldError - error)) + ", b = "+ Arrays.toString(b2));
            if (Math.abs(oldError - error) <= precision) {
                break;
            }
            oldError = error;
            double[][] jacobs = jacob(b2, x, y.length);
            double[][] values = transjacob(jacobs, res);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                IntStream.range(0, values.length).forEach(j -> b2[j] = b2[j] - gamma * values[j][0]);
            }
        }
        return b2;

    }

    /**
     * Root mean square (RMS) error of residuals matrix.
     * @param res The input matrix that contains the error in predictions
     * @return The root mean square of error values in res matrix
     */
    public double calculateError(double[][] res) {
        double sum = 0;
        for (int i = 0; i < res.length; i++) {
            sum += (res[i][0] * res[i][0]);
        }
        return Math.sqrt(sum);
    }

    /**
     * It is the difference between predicted values and the actual values;
     * This is set for minimisation. For optimisation res[i] = actual[i] - predicted[i]
     * @param x The independent variable matrix
     * @param y The dependent values
     * @param b The parameter matrix
     * @return An array with 1 column and number of rows equal to the length of y and its value in row i is the difference between
     * actual y and predicted y at i
     */
    public double[][] calculateResiduals(double[][] x, double[] y, double[] b) {
        double[][] res = new double[y.length][1];

        for (int i = 0; i < res.length; i++) {
            res[i][0] = findY(x[i][0], b) - y[i];
        }
        return res;
    }

    /**
     * Given J (i.e. Jacobian matrix) as input,
     * this method calculates:
     *   (JT * J)^-1 * JT * r
     * It means:
     * first find transpose of matrix J to get JT
     * then multiply JT to J: JT * J
     * then inverse this new matrix: (JT * J)^-1
     * then multiply the inverse matrix by transposed matrix: (JT * J)^-1 * JT
     * then multiply the above matrix by r matrix: (JT * J)^-1 * JT * r
     *
     * @param JArray The Jacobian matrix as input
     * @param res The residulas matrix
     * @return (JT * J)^-1 * JT
     * @throws NoSquareException
     */
    public double[][] transjacob(double[][] JArray, double[][] res) throws NoSquareException {
        Matrix r = new Matrix(res); // r
        Matrix J = new Matrix(JArray); // J
        Matrix JT = MatrixMathematics.transpose(J); // JT
        Matrix JTJ = MatrixMathematics.multiply(JT, J); // JT * J
        Matrix JTJ_1 = MatrixMathematics.inverse(JTJ); // (JT * J)^-1
        Matrix JTJ_1JT = MatrixMathematics.multiply(JTJ_1, JT); // (JT * J)^-1 * JT
        Matrix JTJ_1JTr = MatrixMathematics.multiply(JTJ_1JT, r); // (JT * J)^-1 * JT * r
        return JTJ_1JTr.getValues();
    }

    /**
     * Calculate the Jacobian matrix.
     * It is assumed that matrix x[i][j] has only one column; i.e.only one independent variable. x[i][0] is the ith observaion.
     * b[] is a 1-dimensional array that holds the unknown parameters in function y.
     *
     * Jacobian matrix is a matrix with number of unknown variables given in array b as the number of columns and the number of rows given in
     * 2-dimensional array x as the number of rows.
     * J(i,j) is the Jacobian of row i and variable with index j; i.e. b[j].
     * To calculate J(i,j), first get the value of row i from x; i.e. x[i][0] and then get the value of variable b[j] and then
     * find the value of partial derivative of y with respect to b[j]:
     * J(i,j) = dy/d(b[j]) with given values x[i][0] and b[j]
     *
     * @param b The array of unknown parameters in y function
     * @param x The value of independent variable observed
     * @param numberOfObservations the length of x matrix (the number of rows)
     * @return Jacobian matrix
     */
    public double[][] jacob(double[] b, double[][] x, int numberOfObservations) {
        int numberOfVariables = b.length;
        double[][] jc = new double[numberOfObservations][numberOfVariables];

        for (int i = 0; i < numberOfObservations; i++) {
            for (int j = 0; j < numberOfVariables; j++) {
                jc[i][j] = derivative(x[i][0], b, j);
            }
        }
        return jc;
    }

    /**
     * Calculates the partial derivative of a function at a given point of curve y vs x with respect of unknown variable
     * b[bIndex]
     * e.g. y = (a1 + a2 * x)/ (x + a3)
     * with b[] = {a1, a2, a3}
     * when calculating dy/d(a1) ato point x=1.0, then bIndex = 0.
     * It first changes b[bIndex] = a1 to a1 + alpha or a1 + 1e-6 and calculate y as y1
     * It does the same with a1 - alpha or a1 - 1e-6 and calculate y as y2
     * dy/d(a1) = (y1 - y2)/(2 * alpha)
     * This result is an approximate value.
     * @param x the point on the y vs x curve
     * @param b the array of unknown variables
     * @param bIndex the index of variable for derivation
     *
     * @return dy/d(b[bIndex])
     *
     */
    public double derivative(double x, double[] b, int bIndex) {
        double[] bCopy = b.clone();
        bCopy[bIndex] += alpha;
        double y1 = findY(x, bCopy);
        bCopy = b.clone();
        bCopy[bIndex] -= alpha;
        double y2 = findY(x, bCopy);
        return (y1 - y2) / (2 * alpha);
    }

    /**
     * The function with unknown variables b and and a single observation x;
     * e.g. y = (a1 + a2 * x)/ (x + a3)
     * In this example: b[] = {a1, a2, a3}
     * when x = 1.0 and b[] = {1, 2, 3}
     * y = (1 + 2 *1.0)/(1.0 + 3) = 3/4
     *
     * @param x The given point of independent variable
     * @param b The array with a given set of values for unknown variables in y function
     * @return The value of dependent variable y with given x and b[]
     *
     */
    public abstract double findY(double x, double[] b);


}
