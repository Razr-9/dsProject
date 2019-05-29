using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

using Microsoft.Xna.Framework;

namespace Zelda2D
{
    public class ActorManager: GameComponent
    {
        List<Actor> actors = new List<Actor>();
        Game1 game;

        public ActorManager(Game game)
            : base(game)
        {
            this.game = game as Game1;
        }

        public int Add(Actor actor)
        {
            actors.Add(actor);
            game.QuadTree.Add(actor);
            // return an ID for quadtree
            return actors.Count - 1;
        }

        public override void Update(GameTime gameTime)
        {
            foreach (Actor a in actors)
            {
                // actor and wall collisions
                Wall w = game.WallManager.CheckIntersection(a.BoundingBox);
                int i = 0;
                while (w != null && i < 10)
                {
                    a.CollisionWithWall(w);
                    w = game.WallManager.CheckIntersection(a.BoundingBox);
                    i++;
                }
                a.Update();

                game.QuadTree.UpdatePosition(a);
            }
        }
    }
}
