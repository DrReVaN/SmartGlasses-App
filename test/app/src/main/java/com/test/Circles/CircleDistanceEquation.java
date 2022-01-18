package com.test.Circles;

public class CircleDistanceEquation
{
    public double[] x;
    public double[] y;
    public double d;
    public double xm;
    public double ym;

    public CircleEquation equ;

    public CircleDistanceEquation() { }

    /// <summary>
    /// Initializes a new instance of the <see cref="CircleDistanceEquation"/> class.
    /// </summary>
    /// <param name="_d">The _d.</param>
    /// <param name="_xm">The _XM.</param>
    /// <param name="_ym">The _ym.</param>
    public CircleDistanceEquation(double _d,double _xm,double _ym){
        d = _d;
        xm = _xm;
        ym = _ym;

        equ = new CircleEquation(1, -2 * xm, 1, -2 * ym, (Math.pow(xm,2) + Math.pow(ym,2)));
    }
}
