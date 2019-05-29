#ifndef __PHYSICS_H
#define __PHYSICS_H

#include <cmath>
#include <list>
#pragma warning(disable:4244)

template <class T>
inline
T sign (T x)
{
	if (x > 0) {
		return 1;
	}
	if (x < 0) {
		return -1;
	}
	return 0;
}

struct Vector2D {
	friend void wrap (Vector2D&, double, double);
	double x;
	double y;
	Vector2D (double x = 0, double y = 0)
	{
		this->x = x;
		this->y = y;
	}
	Vector2D (const Vector2D& v): x(v.x), y(v.y) {}
	Vector2D& operator = (const Vector2D& v)
	{
		x = v.x;
		y = v.y;
		return *this;
	}
	Vector2D& operator = (double s)
	{
		x = s;
		y = s;
	}
	Vector2D operator + (const Vector2D& v) const
	{
		return Vector2D(x + v.x, y + v.y);
	}
	Vector2D operator - (const Vector2D& v) const
	{
		return Vector2D(x - v.x, y - v.y);
	}
	Vector2D operator * (const Vector2D& v) const
	{
		return Vector2D(x * v.x, y * v.y);
	}
	Vector2D operator / (const Vector2D& v) const
	{
		return Vector2D(x / v.x, y / v.y);
	}
	Vector2D operator + (double s) const
	{
		return Vector2D(x + s, y + s);
	}
	Vector2D operator - (double s) const
	{
		return Vector2D(x - s, y - s);
	}
	Vector2D operator * (double s) const
	{
		return Vector2D(x * s, y * s);
	}
	Vector2D operator / (double s) const
	{
		return Vector2D(x / s, y / s);
	}
	Vector2D& operator += (const Vector2D& v)
	{
		x += v.x;
		y += v.y;
		return *this;
	}
	Vector2D& operator -= (const Vector2D& v)
	{
		x -= v.x;
		y -= v.y;
		return *this;
	}
	Vector2D& operator *= (const Vector2D& v)	
	{
		x *= v.x;
		y *= v.y;
		return *this;
	}
	Vector2D& operator /= (const Vector2D& v)
	{
		x /= v.x;
		y /= v.y;
		return *this;
	}
	Vector2D& operator += (double s)			// operator+= is used to add another Vector2D to this Vector2D.
	{
		x += s;
		y += s;
		return *this;
	}
	Vector2D& operator -= (double s)			// operator-= is used to subtract another Vector2D from this Vector2D.
	{
		x -= s;
		y -= s;
		return *this;
	}
	Vector2D& operator *= (double s)			// operator*= is used to scale this Vector2D by a s.
	{
		x *= s;
		y *= s;
		return *this;
	}
	Vector2D& operator /= (double s)			// operator/= is used to scale this Vector2D by a s.
	{
		x /= s;
		y /= s;
		return *this;
	}

	Vector2D operator - () const
	{
		return Vector2D(-x, -y);
	}

	bool operator == (Vector2D v) const
	{
		return x == v.x && y == v.y;
	}
	bool operator == (double s) const
	{
		return x == s && y == s;
	}
	bool operator != (Vector2D v) const
	{
		return !(*this == v);
	}
	bool operator != (double s) const
	{
		return !(*this == s);
	}
	bool operator > (Vector2D v) const
	{
		return x > v.x && y > v.y;
	}
	bool operator < (Vector2D v) const
	{
		const Vector2D& u = *this;
		return (!(u > v) && !(u == v));
	}

	double length () const
	{
		return sqrt(x*x + y*y);
	}

	Vector2D abs () const
	{
		return Vector2D(::abs(x), ::abs(y));
	}

	void clear ()
	{
		x = 0;
		y = 0;
	}

	void normalise ()
	{
		double length = this->length();

		if (length == 0) {
			length = 1;
		}
		x /= length;
		y /= length;
	}
	Vector2D unitise () const
	{
		double length = this->length();
		if (length == 0) {
			return *this;
		}
		return Vector2D(x / length, y / length);
	}
};
Vector2D operator + (double s, Vector2D v)
{
	v += s;
	return v;
}
Vector2D operator - (double s, Vector2D v)
{
	v -= s;
	return v;
}
Vector2D operator / (double s, Vector2D v)
{
	v /= s;
	return v;
}
Vector2D operator * (double s, Vector2D v)
{
	v *= s;
	return v;
}

const long double PI = 3.14159265358979323846264338327950288419716939937510L;
inline
double distanceToPoint (const Vector2D& p1, const Vector2D& p2)
{
	return sqrt((p2.x - p1.x) * (p2.x - p1.x) + (p2.y - p1.y) * (p2.y - p1.y));
}
inline
double dirToPoint (const Vector2D& p1, const Vector2D& p2)
{
	return atan2(p1.y - p2.y, p2.x - p1.x) * 180 / PI;
}
inline
Vector2D lengthdir (double length, double dir)
{
	return Vector2D(cos(dir * PI / 180) * length, sin(dir * PI / 180) * length);
}
inline
Vector2D lengthdir (Vector2D& v1, Vector2D& v2)
{
	double dir = abs(dirToPoint(v1, v2));
	double len = distanceToPoint(v1, v2);
	return lengthdir(len, dir);
}
#endif