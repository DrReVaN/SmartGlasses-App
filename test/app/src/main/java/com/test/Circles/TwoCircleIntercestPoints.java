package com.test.Circles;


public class TwoCircleIntercestPoints
{
   public CircleDistanceEquation a = new CircleDistanceEquation();
   public CircleDistanceEquation b = new CircleDistanceEquation();


   public double[] x = new double[2];
   public double[] y = new double[2];

   public TwoCircleIntercestPoints(CircleDistanceEquation _first, CircleDistanceEquation _second) {
       b = _second;
       a = _first;
   }

   public double SolveQuadratic(double a, double b, double c , boolean is_x , boolean is_assign)
   {
       double sqrtpart = b * b - 4 * a * c;
       double img;

       if (is_x)
       {
           if (sqrtpart > 0)
           {
               x[0] = (-b + Math.sqrt(sqrtpart)) / (2 * a);
               x[1] = (-b - Math.sqrt(sqrtpart)) / (2 * a);

           }
           else if (sqrtpart < 0)
           {
               sqrtpart = -sqrtpart;
               x[0] = x[1] = -b / (2 * a);
               img = Math.sqrt(sqrtpart) / (2 * a);
               x[0] = x[0] + img;
               x[1] = x[1] - img;
           }
           else
           {
               if (is_assign)
                   x[0] = x[1] = (-b + Math.sqrt(sqrtpart)) / (2 * a);
               else
                   return (-b + Math.sqrt(sqrtpart)) / (2 * a);
           }
       }
       else {
           if (sqrtpart > 0)
           {
               y[0] = (-b + Math.sqrt(sqrtpart)) / (2 * a);
               y[1] = (-b - Math.sqrt(sqrtpart)) / (2 * a);
           }
           else if (sqrtpart < 0)
           {
               sqrtpart = -sqrtpart;
               y[0] = y[1] = -b / (2 * a);
               img = Math.sqrt(sqrtpart) / (2 * a);
               y[0] = y[0] + img;
               y[1] = y[1] - img;
           }
           else
           {
               if (is_assign)
                   y[0] = y[1] = (-b + Math.sqrt(sqrtpart)) / (2 * a);
               else
                   return (-b + Math.sqrt(sqrtpart)) / (2 * a);
           }
       }

       return 0;
   }

   public void compute()
   {
       CircleEquation res = new CircleEquation(0, 0, 0, 0, 0);

       res.x2 = 0;

       res.x = b.equ.x - a.equ.x;
       res.y2 = b.equ.y2 - a.equ.y2;
       res.y = b.equ.y - a.equ.y;
       res.num = b.equ.num - a.equ.num;
       double temp = Math.pow(b.d, 2) - Math.pow(a.d, 2);
       res.num -= temp;

       if (res.x != 0 && res.y != 0)
       {
           res.y /= res.x;
           res.num /= res.x;
           res.x /= res.x;
           res.y -= (2 * res.y);
           res.num -= (2 * res.num);

           temp = res.num * res.y;
           temp -= 2 * temp;

           SolveQuadratic((a.equ.x2 * Math.pow(res.y, 2)) + a.equ.y2,
                   ((-2) * temp) + (a.equ.x * res.y) + a.equ.y,
                   (a.equ.x2 * Math.pow(res.num, 2)) + (a.equ.x * res.num) + a.equ.num - Math.pow(a.d, 2) ,false,true);

           x[0] = res.num + (y[0] * res.y);
           x[1] = res.num + (y[1] * res.y);

       }
       else if (res.x == 0 && res.y != 0)
       {
           y[0] = y[1] = (-1 * res.num) / res.y;


           a.equ.num -= Math.pow(a.d, 2);
           double tmp = 0;

           tmp += (a.equ.y * y[0]) + (a.equ.y2 * Math.pow(y[0], 2)) + a.equ.num;

           SolveQuadratic(a.equ.x2, a.equ.x, tmp, true ,false);


           b.equ.num -= Math.pow(b.d, 2);
           double tmp2 = 0;

           tmp2 += (b.equ.y * y[1]) + (b.equ.y2 * Math.pow(y[1], 2)) + b.equ.num;

           SolveQuadratic(b.equ.x2, b.equ.x, tmp2, true, false);



       }
       else {
           x[0] = x[1] = (-1 * res.num) / res.x;

           a.equ.num -= Math.pow(a.d, 2);
           double tmp = 0;

           tmp += (a.equ.x * x[0]) + (a.equ.x2 * Math.pow(x[0], 2)) + a.equ.num;

           SolveQuadratic(a.equ.y2, a.equ.y, tmp, false, false);


           b.equ.num -= Math.pow(b.d, 2);
           double tmp2 = 0;

           tmp2 += (b.equ.x * x[1]) + (b.equ.x2 * Math.pow(x[1], 2)) + b.equ.num;

           SolveQuadratic(b.equ.y2, b.equ.y, tmp2, false, false);
       }

   }
}
