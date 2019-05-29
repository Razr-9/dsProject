using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

using Microsoft.Xna.Framework;
using Microsoft.Xna.Framework.Graphics;

namespace FPS.GameComponents
{
    public static class Loader
    {
        public static Model loadModel(FirstPerson game, string name)
        {
            Model ret = game.Content.Load<Model>(name);
            foreach (ModelMesh mesh in ret.Meshes)
            {
                foreach (ModelMeshPart part in mesh.MeshParts)
                {
                    part.Effect = game.effect.Clone();
                }
            }
            return ret;
        }
    }
}
