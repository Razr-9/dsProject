/*
*  Thin wrapper of A* algorithm by Justin Heyes-Jones
*  Designed specially for pathfinding use
*  All code copyright (C)2001-2005 Justin Heyes-Jones
*
*  Platform independent
*  Not thread-safe
*/

#include "astarimpl.h"
#include <vector>

struct Pos
{
	int x;
	int y;

	bool operator == (const Pos& rhs) { return x == rhs.x && y == rhs.y; }

	Pos (int _x, int _y): x(_x), y(_y) {}
	Pos (): x(0), y(0) {}
};
typedef std::vector<Pos> Path;
class Map
{
	public:
		// Uses RAII
		Map (int width, int height)
			: MAP_WIDTH(width), MAP_HEIGHT(height), reCache(false)
		{
			map.resize(width * height, 1);
		}
		
		// Returns a complete path as a vector
		Path search (Pos start, Pos end);

		// Returns a step on the path. Useful for paths that change a lot
		Pos searchNextMove (Pos start, Pos end);
		
		// Marks a square of terrain.
		// If difficulty = 9, the square is non-walkable
		// If difficulty = 0, the square is "free" (takes 0 time to walk)
		// If 0 < difficulty <= 5, the square becomes increasingly difficult to walk
		// All squares are initialised to 1
		void mark (int x, int y, int difficulty = 9) { map[y * MAP_WIDTH + x] = difficulty; }

		// Reset map to all open
		void clear () { for (int i = 0; i < MAP_WIDTH * MAP_HEIGHT; ++i) { map[i] = 1; } }

		// Resize map
		// Old squares are left at their value
		// Clears cache
		void resize (int width, int height ) { map.resize(width * height, 1); MAP_WIDTH = width; MAP_HEIGHT = height; }

		const std::vector<int>& getMap () const { return map; }

	private:
		class Node
		{
		public:
			unsigned int x;
			unsigned int y;	
	
			Node() { x = y = 0; }
			Node( unsigned int px, unsigned int py ) { x=px; y=py; }

			float GoalDistanceEstimate( Node &nodeGoal );
			bool IsGoal( Node &nodeGoal );
			bool GetSuccessors( AStarSearch<Node> *astarsearch, Node *parent_node );
			float GetCost( Node &successor );
			bool IsSameState( Node &rhs );

			void PrintNodeInfo(); 
		};


		int MAP_WIDTH;
		int MAP_HEIGHT;

		std::vector<int> map;
		static Map* set;

		bool reCache;


		void acquire () { set = this; }

		static int getMap(int x, int y )
		{

			if( x < 0 ||
				x >= set->MAP_WIDTH ||
				 y < 0 ||
				 y >= set->MAP_HEIGHT
			  )
			{
				return 9;	 
			}

			return set->map[(y*set->MAP_WIDTH)+x];
		}
};
Map* Map::set;

namespace mp
{
	using namespace ::std;
	static vector<Map> maps;
	unsigned int addMap (int width, int height)
	{
		maps.push_back(Map(width, height));
		return maps.size() - 1;
	}
	Map& getMap (unsigned int id)
	{
		return maps[id];
	}
}

Path Map::search (Pos start, Pos end)
{
	acquire();

	static bool cached = false;
	static Pos oStart, oEnd;
	static std::vector<Pos> ret;
	if (reCache) {
		cached = false;
		reCache = false;
	}
	if (!cached) {
		oStart = start;
		oEnd = end;
		cached = true;
	}
	else {
		if (oStart == start && oEnd == end) {
			return ret;
		}
		else {
			ret.clear();
		}
	}
	AStarSearch<Node> src;
	int searchCount = 0;
	const int NumSearches = 1;

	while (searchCount < NumSearches) {
		Node nStart, nEnd;
		nStart.x = start.x;
		nStart.y = start.y;
		nEnd.x = end.x;
		nEnd.y = end.y;

		src.SetStartAndGoalStates(nStart, nEnd);
		int SearchState;
		int SearchSteps = 0;
		do {
			SearchState = src.SearchStep();
			SearchSteps++;
		}
		while (SearchState == AStarSearch<Node>::SEARCH_STATE_SEARCHING);
		if (SearchState == AStarSearch<Node>::SEARCH_STATE_SUCCEEDED) {
			Node s = *(src.GetSolutionStart());
			ret.push_back(Pos(s.x, s.y));
			for (;;) {
				Node* n = src.GetSolutionNext();
				if (!n) {
					break;
				}
				
				ret.push_back(Pos(n->x, n->y));
			}
			return ret;
		}
		else if (SearchState == AStarSearch<Node>::SEARCH_STATE_FAILED) {
			// throw out, user to catch
			throw std::exception("Path not found");
		}
	}
	// execution should never reach here
	throw std::out_of_range("Concurrency error");
}
Pos Map::searchNextMove (Pos start, Pos end)
{
	acquire();

	// TODO: caching
	AStarSearch<Node> src;
	int searchCount = 0;
	const int NumSearches = 1;

	while (searchCount < NumSearches) {
		Node nStart, nEnd;
		nStart.x = start.x;
		nStart.y = start.y;
		nEnd.x = end.x;
		nEnd.y = end.y;

		src.SetStartAndGoalStates(nStart, nEnd);
		int SearchState;
		int SearchSteps = 0;
		do {
			SearchState = src.SearchStep();
			SearchSteps++;
		}
		while (SearchState == AStarSearch<Node>::SEARCH_STATE_SEARCHING);
		if (SearchState == AStarSearch<Node>::SEARCH_STATE_SUCCEEDED) {
			src.GetSolutionStart();
			Node n = *(src.GetSolutionNext());
			return Pos(n.x, n.y);
		}
		else if (SearchState == AStarSearch<Node>::SEARCH_STATE_FAILED) {
			// throw out, user to catch
			throw std::exception("Path not found");
			// execution should never reach here
			return Pos(-1, -1);
		}
	}
	// execution should never reach here
	throw std::out_of_range("Concurrency error");
}

bool Map::Node::IsSameState( Map::Node &rhs )
{

	// same state in a maze search is simply when (x,y) are the same
	if( (x == rhs.x) &&
		(y == rhs.y) )
	{
		return true;
	}
	else
	{
		return false;
	}

}

void Map::Node::PrintNodeInfo()
{
}

// Here's the heuristic function that estimates the distance from a Node
// to the Goal. 

float Map::Node::GoalDistanceEstimate( Map::Node &nodeGoal )
{
	float xd = fabs(float(((float)x - (float)nodeGoal.x)));
	float yd = fabs(float(((float)y - (float)nodeGoal.y)));

	return xd + yd;
}

bool Map::Node::IsGoal( Map::Node &nodeGoal )
{

	if( (x == nodeGoal.x) &&
		(y == nodeGoal.y) )
	{
		return true;
	}

	return false;
}

// This generates the successors to the given Node. It uses a helper function called
// AddSuccessor to give the successors to the AStar class. The A* specific initialisation
// is done for each node internally, so here you just set the state information that
// is specific to the application
bool Map::Node::GetSuccessors( AStarSearch<Map::Node> *astarsearch, Map::Node *parent_node )
{

	int parent_x = -1; 
	int parent_y = -1; 

	if( parent_node )
	{
		parent_x = parent_node->x;
		parent_y = parent_node->y;
	}
	

	Map::Node NewNode;

	// push each possible move except allowing the search to go backwards

	if( (getMap( x-1, y ) < 9) 
		&& !((parent_x == x-1) && (parent_y == y))
	  ) 
	{
		NewNode = Map::Node( x-1, y );
		astarsearch->AddSuccessor( NewNode );
	}	

	if( (getMap( x, y-1 ) < 9) 
		&& !((parent_x == x) && (parent_y == y-1))
	  ) 
	{
		NewNode = Map::Node( x, y-1 );
		astarsearch->AddSuccessor( NewNode );
	}	

	if( (getMap( x+1, y ) < 9)
		&& !((parent_x == x+1) && (parent_y == y))
	  ) 
	{
		NewNode = Map::Node( x+1, y );
		astarsearch->AddSuccessor( NewNode );
	}	

		
	if( (getMap( x, y+1 ) < 9) 
		&& !((parent_x == x) && (parent_y == y+1))
		)
	{
		NewNode = Map::Node( x, y+1 );
		astarsearch->AddSuccessor( NewNode );
	}	

	return true;
}

// given this node, what does it cost to move to successor. In the case
// of our map the answer is the map terrain value at this node since that is 
// conceptually where we're moving

float Map::Node::GetCost( Map::Node &successor )
{
	return (float) getMap( x, y );

}