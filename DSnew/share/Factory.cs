using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

using Microsoft.Xna.Framework;

namespace FPS.Entity
{
    public static class Factory
    {
        public static Entity Create(int type, Game game, Vector3 position)
        {
            switch (type)
            {
                case 0:
                    return null;

                case 1:
                    return new Mushroom(game, 1, position);

                default:
                    throw new NotImplementedException();
            }
        }
    }
}
