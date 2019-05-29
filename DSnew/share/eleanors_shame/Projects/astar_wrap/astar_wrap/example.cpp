#include "astar.h"
#include <iostream>

int main ()
{
	// # = nowalk
	// @ = begin
	// $ = end
	//	 0 1 2 3 4
	// 0 @     # #
	// 1   #     
	// 2   # # # 
	// 3     #   
	// 4 #       $

	mp::addMap(5, 5);
	
	Map map = mp::getMap(0);

	map.mark(3, 0);
	map.mark(4, 0);

	map.mark(1, 1);

	map.mark(1, 2);
	map.mark(2, 2);
	map.mark(3, 2);

	map.mark(2, 3);

	map.mark(0, 4);

	Path p = map.search(Pos(0, 0), Pos(4, 4));
	for (int i = 0; i < p.size(); ++i) {
		std::cout << "(" << p[i].x << ", " << p[i].y << ")\n";
	}

	getchar();
	return 0;
}
