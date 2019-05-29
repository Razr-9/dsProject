#include <fstream>
#include <vector>
#include <string>
#include <algorithm>

namespace bmp {
	typedef unsigned char byte;

	struct pixel {
		pixel (int xv, int yv, byte r, byte g, byte b): x(xv), y(yv), red(r), green(g), blue(b) {}
		pixel (int xv, int yv, byte b, byte g, byte r, int dummy): x(xv), y(yv), red(r), green(g), blue(b) { (dummy); }
		int x;
		int y;
		byte red;
		byte green;
		byte blue;
	};

	void load_bin (std::vector<byte>& buffer, const std::string& filename);
	void load_pixels (std::vector<pixel>& buffer, const std::string& filename);
}