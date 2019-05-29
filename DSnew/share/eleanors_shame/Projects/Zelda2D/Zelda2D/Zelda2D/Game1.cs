using System;
using System.Collections.Generic;
using System.Linq;
using Microsoft.Xna.Framework;
using Microsoft.Xna.Framework.Audio;
using Microsoft.Xna.Framework.Content;
using Microsoft.Xna.Framework.GamerServices;
using Microsoft.Xna.Framework.Graphics;
using Microsoft.Xna.Framework.Input;
using Microsoft.Xna.Framework.Media;

namespace Zelda2D
{
    public class Game1 : Microsoft.Xna.Framework.Game
    {
        GraphicsDeviceManager graphics;
        GraphicsDevice device;
        SpriteBatch spriteBatch;
        Input input;
        Player player;
        ActorManager actorManager;
        WallManager wallManager;
        RectangleTree<Object> quadTree = new RectangleTree<Object>((obj => obj.GetPosition()), new Rectangle(0, 0, 800, 480));

        public SpriteBatch SpriteBatch { get { return spriteBatch; } }
        public Input Input { get { return input; } }
        public ActorManager ActorManager { get { return actorManager; } }
        public WallManager WallManager { get { return wallManager; } }
        public RectangleTree<Object> QuadTree { get { return quadTree; } }

        public Game1()
        {
            graphics = new GraphicsDeviceManager(this);
            device = GraphicsDevice;
            Content.RootDirectory = "Content";

            // components
            Components.Add(input = new Input(this));
            Components.Add(actorManager = new ActorManager(this));
            Components.Add(wallManager = new WallManager(this));
            Components.Add(player = new Player(this));

            for (int i = 0; i < 25; i++)
            {
                new Wall(this, new Vector2(i * 32, 0));
                new Wall(this, new Vector2(i * 32, 448));
            }
            for (int i = 1; i < 14; i++)
            {
                new Wall(this, new Vector2(0, i * 32));
                new Wall(this, new Vector2(768, i * 32));
            }
            new Wall(this, new Vector2(64, 64));
            Random r = new Random();
            for (int i = 0; i < 20; i++)
            {
                new Wall(this, new Vector2(r.Next(800), r.Next(416) + 32));
            }
        }

        protected override void Initialize()
        {
            base.Initialize();
        }

        protected override void LoadContent()
        {
            spriteBatch = new SpriteBatch(GraphicsDevice);
        }

        protected override void UnloadContent()
        {
        }

        protected override void Update(GameTime gameTime)
        {
            base.Update(gameTime);
        }

        protected override void Draw(GameTime gameTime)
        {
            GraphicsDevice.Clear(Color.ForestGreen);

            spriteBatch.Begin();
            base.Draw(gameTime);
            spriteBatch.End();
        }
    }
}
