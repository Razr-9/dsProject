#pragma once

#include <glutwrap.h>
#include <cstdio>
#include <windows.h>
#include __GLUT_INCLUDE
#include <gl/gl.h>
#include <gl/glu.h>
#include <stack>
#include <vector>
#include <utility>
#include <string>
#include <iostream>
#include <fstream>
#include <ctime>
#include <string>
#include <sstream>
#include <process.h>
#include <bitmap.h>
#include <pngload.h>

unsigned int CSprite::single_texture =					0;
unsigned long CSprite::width =							0;
unsigned long CSprite::height =							0;
t_load CSprite::default_func =							0;

CGlutWrapper2D *CGlutWrapper2D::_static_ptr =			nullptr;
CSprite CGlutWrapper2D::_empty =						CSprite();
const CTextureVector CGlutWrapper2D::tv_empty =			CTextureVector();
t_view CGlutWrapper2D::current_view =					0;
const float CGlutWrapper2D::_View_tolerance =			128;

const CObject* CObject::noone =							nullptr;

void glutClearColour(COLOUR c, float alpha = 1.0f)
{
	glClearColor(c.red / 255, c.green / 255, c.blue / 255, alpha);
}
void stamp (const char* what)
{
	static const long long start = gw::ticks();
	long long time = gw::ticks();
	std::cout << "Time: " << time << "\t";
	if (time < 10) {
		std::cout << '\t';
	}
	std::cout << what;
}
#pragma warning(push)
#pragma warning(disable:4100)
inline
void stamp_if (const char* what)
{
#ifdef __GLUT_DEBUG
	stamp(what);
#endif
}
#pragma warning(pop)

bool CGlutWrapper2D::_Sort_func (CObject* i, CObject* j)
{
	return i->depth < j->depth;
}
void CGlutWrapper2D::orth_start () const
{
	glMatrixMode(GL_PROJECTION);
	glPushMatrix();
	glLoadIdentity();

	gluOrtho2D(0, window_width, 0, window_height);
	glScalef(1, -1, 1);
	glTranslatef(0, -(float)window_height, 0);

	glMatrixMode(GL_MODELVIEW);
}
void CGlutWrapper2D::orth_end () const
{
	glMatrixMode(GL_PROJECTION);
	glPopMatrix();
	glMatrixMode(GL_MODELVIEW);
}
void CGlutWrapper2D::render ()
{
	int i;
	render_count++;
	if (render_count < 5) {
		return;
	}
	if (render_count == 5) {
#ifdef __GLUT_DEBUG
		std::cout << "Game beginning; took " << gw::ticks()/ 1000000.0 << " seconds to start.\n";
#endif
		for (i = 0; i < (signed)obj_array.size(); ++i) {
			obj_array[i]->begin();
		}
	}
	delta_time = gw::ticks() - old_time;
	old_time += delta_time;
	
		/*int loops = 0;
		while (gw::ticks() > next_tick && loops < MAX_FRAMESKIP) {*/
			for (i = 0; i < (signed)obj_array.size(); ++i) {
				obj_array.at(i)->step();
			}
			for (i = 0; i < 1000; ++i) {
				if (i < 256) {
					keyboard[i].pressed = false;
					keyboard[i].released = false;
				}
				keyboard_ex[i].pressed = false;
				keyboard_ex[i].released = false;
			}/*
			loops++;
			next_tick += SKIP_TICKS;
		}
		float interpol = (float)gw::ticks();
		interpol += SKIP_TICKS;
		interpol -= next_tick;
		interpol /= SKIP_TICKS;*/
		for (i = 0; i < (signed)obj_array.size(); ++i) {
			obj_array.at(i)->draw();
		}

		for (i = 0; i < (signed)tile_array.size(); ++i) {
			tile_array[i]->draw();
		}

	if (in_loop && ended) {
		glutLeaveMainLoop();
		return;
	}
	
	glutClearColour(clear_col);
	glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
	glLoadIdentity();

	orth_start();

		while(!render_stack.empty()) {	
			glBegin(GL_QUADS);
				if(tex_coord_stack.empty()) {
					throw std::runtime_error("No texture coordinates on the stack.");
				}
				float x, y, xspeed, yspeed;
				CTextureVector t_coord;
				t_coord = tex_coord_stack.top();
				tex_coord_stack.pop();
				xspeed = render_pop();
				yspeed = render_pop();
				for (i = 3; i >= 0; --i) {

					x = render_pop();
					y = render_pop();
					if (current_view < views.size()) {
						CViewInfo it = views[current_view];
						if (xspeed != 0 || yspeed != 0) {
							int dummy;
							dummy = 3;
						}
						x -= it.x;
						y -= it.y;
						/*x += (xspeed * interpol);
						y += (yspeed * interpol);*/
						if (y < it.h - it.y + _View_tolerance && x < it.w - it.x + _View_tolerance) {
							if (!t_coord.empty()) {
								glTexCoord2f(t_coord.at_a(i), t_coord.at_b(i));
							}
							glVertex2f(x, y);
						}
					}
				}
			glEnd();
		}

	orth_end();

	glutSwapBuffers();

	handle_errors();
}
void CGlutWrapper2D::end ()
{
	ended = true;
#ifdef __GLUT_DEBUG
	stamp("Ending.\n");
#endif
}
void CGlutWrapper2D::render_push (COLOUR col, unsigned int polygon, const std::vector<float>& vertices,
	const CTextureVector& t_vector)
{
	int i;
	for(i = vertices.size() - 1; i >= 0; --i) {
		render_stack.push(vertices[i]);
	}

	render_stack.push((float)polygon);

	render_stack.push(col.red);
	render_stack.push(col.green);
	render_stack.push(col.blue);

	if (!t_vector.empty()) {
		tex_coord_stack.push(t_vector);
	}
}
void CGlutWrapper2D::draw_rectangle (float x, float y, float width, float height, COLOUR col,
	const CTextureVector& t_vector, double xspeed, double yspeed) {
	render_stack.push(y + height);
	render_stack.push(x);
	render_stack.push(y + height);
	render_stack.push(x + width);
	render_stack.push(y);
	render_stack.push(x + width);
	render_stack.push(y);
	render_stack.push(x);
	render_stack.push((float)yspeed);
	render_stack.push((float)xspeed);
	tex_coord_stack.push(t_vector);
}

void CGlutWrapper2D::key_down (byte key, int x, int y)
{
	UNUSED(x);
	UNUSED(y);
	keyboard[key].down = true;
	keyboard[key].pressed = true;
}
void CGlutWrapper2D::key_up (byte key, int x, int y)
{
	UNUSED(x);
	UNUSED(y);
	keyboard[key].down = false;
	keyboard[key].released = true;
}
bool CGlutWrapper2D::get_key (byte key) const
{
	return keyboard[key].down;
}
void CGlutWrapper2D::key_down_ex (int key, int x, int y)
{
	UNUSED(x);
	UNUSED(y);
	keyboard_ex[key].down = true;
	keyboard_ex[key].pressed = true;

	int mod = glutGetModifiers();
	if (mod & GLUT_KEY_SHIFT) {
		keyboard_ex[GLUT_KEY_SHIFT].down = true;
		keyboard_ex[GLUT_KEY_SHIFT].pressed = true;
	}
	if (mod & GLUT_KEY_CTRL) {
		keyboard_ex[GLUT_KEY_CTRL].down = true;
		keyboard_ex[GLUT_KEY_CTRL].pressed = true;
	}
	if (mod & GLUT_KEY_ALT) {
		keyboard_ex[GLUT_KEY_ALT].down = true;
		keyboard_ex[GLUT_KEY_ALT].pressed = true;
	}
}
void CGlutWrapper2D::key_up_ex (int key, int x, int y)
{
	UNUSED(x);
	UNUSED(y);
	keyboard_ex[key].down = false;
	keyboard_ex[key].released = true;

	int mod = glutGetModifiers();
	if (!(mod & GLUT_KEY_SHIFT) && keyboard_ex[GLUT_KEY_SHIFT].down) {
		keyboard_ex[GLUT_KEY_SHIFT].down = false;
		keyboard_ex[GLUT_KEY_SHIFT].released = true;
	}
	if (!(mod & GLUT_KEY_CTRL) && keyboard_ex[GLUT_KEY_CTRL].down) {
		keyboard_ex[GLUT_KEY_CTRL].down = false;
		keyboard_ex[GLUT_KEY_SHIFT].released = true;
	}
	if (!(mod & GLUT_KEY_ALT) && keyboard_ex[GLUT_KEY_ALT].down) {
		keyboard_ex[GLUT_KEY_ALT].down = false;
		keyboard_ex[GLUT_KEY_SHIFT].released = true;
	}
}
bool CGlutWrapper2D::get_key_ex (int key) const
{
	return keyboard_ex[key].down;
}
bool CGlutWrapper2D::get_key_pressed (byte key) const
{
	return keyboard[key].pressed;
}
bool CGlutWrapper2D::get_key_pressed_ex (int key) const
{
	return keyboard_ex[key].pressed;
}
bool CGlutWrapper2D::get_key_released (byte key) const
{
	return keyboard[key].released;
}
bool CGlutWrapper2D::get_key_released_ex (int key) const
{
	return keyboard_ex[key].released;
}

void CGlutWrapper2D::reshape (const int w, const int h)
{
	glViewport(0, 0, (GLsizei)w, (GLsizei)h);
	glMatrixMode(GL_PROJECTION);
	glLoadIdentity();
	gluPerspective(60, (GLfloat)w / (GLfloat)h, 0.1, 1000.0);
	window_width = w;
	window_height = h;
	glMatrixMode(GL_MODELVIEW);
}

void CGlutWrapper2D::handle_errors (bool shownoerrors)
{
	switch (glGetError())
	{
		case GL_NO_ERROR:
			if (shownoerrors) std::cout << "No errors recorded.\n";
			break;
		case GL_INVALID_ENUM:
			std::cout << "Invalid enumeration passed; function ignored\n";
			MessageBox(0, "Invalid enum", 0, 0);
			throw std::exception("Invalid enum");
			break;
		case GL_INVALID_VALUE:
			std::cout << "Argument out of range; function ignored\n";
			throw std::exception("Argument out of range");
			break;
		case GL_STACK_OVERFLOW:
			std::cout << "Potential stack overflow found; function ignored\n";
			throw std::exception("Stack overflow");
			break;
		case GL_STACK_UNDERFLOW:
			std::cout << "Potential stack underflow found; function ignored\n";
			throw std::exception("Stack underflow");
			break;
		case GL_OUT_OF_MEMORY:
			std::cout << "Not enough memory available; undefined behaviour\n";
			throw std::exception("Out of memory");
			break;
		default:
			std::cout << "Unknown error\n";
			break;
	}
}

CGlutWrapper2D& CGlutWrapper2D::get_ptr ()
{
	if (_static_ptr == 0) {
		throw std::runtime_error("No CGlutWrapper2D exists");
	}
	else {
		return *_static_ptr;
	}
}

inline
float CGlutWrapper2D::render_pop () { 
	float r = render_stack.top();
	render_stack.pop();
	return r;
}

void CGlutWrapper2D::clear_objects ()
{
	for (t_obj_sz i = 0; i < obj_array.size(); ++i) {
		if (obj_array[i]->_Allocated_new) {
			delete obj_array[i];
		}
	}
	for (auto i = 0u; i < tile_array.size(); ++i) {
		delete obj_array[i];
	}
	obj_array.clear();
}

t_array_return CGlutWrapper2D::get_obj_list (int ty)
{
	obj_array_returnable.clear();

	for (unsigned int i = 0; i < objs; ++i) {
		if (obj_array[i]->get_type() == ty) {
			obj_array_returnable.push_back(obj_array[i]);
		}
	}
	
	return obj_array_returnable;
}
t_array_return CGlutWrapper2D::get_obj_list (const gw::CRange& ty)
{
	obj_array_returnable.clear();

	for (unsigned int i = 0; i < objs; ++i) {
		CObject* c_obj = obj_array[i];
		if (c_obj->get_type() >= ty.get_v1() && c_obj->get_type() <= ty.get_v2()) {
			obj_array_returnable.push_back(obj_array[i]);
		}
	}
	
	return obj_array_returnable;
}
t_array_return CGlutWrapper2D::_get_obj_list (int ty)
{
	obj_array_returnable.clear();

	for (unsigned int i = 0; i < objs; ++i) {
		if (obj_array[i]->get_type() == ty) {
			obj_array_returnable.push_back(obj_array[i]);
		}
	}
	
	return obj_array_returnable;
}
t_array_return CGlutWrapper2D::get_obj_list ()
{
	obj_array_returnable.clear();
	std::copy(obj_array.begin(), obj_array.end(), obj_array_returnable.begin());
	return obj_array_returnable;
}
bool CGlutWrapper2D::_area_free (const CObject* caller, double x1, double y1, double x2, double y2)
{
	double ox1;
	double oy1;
	double ox2;
	double oy2;
	const CObject* ptr = nullptr;
	unsigned int i = 0;
	for (i = 0; i < obj_array.size(); ++i) {
		ptr = obj_array[i];
		ox1 = ptr->get_x();
		oy1 = ptr->get_y();
		ox2 = ox1 + ptr->get_width();
		oy2 = oy1 + ptr->get_height();
		if (ptr != caller) {
			if (!(y2 < oy1 || y1 > oy2 || x2 < ox1 || x1 > ox2)) {
				return false;
			}
		}
	}
	return true;
}

CObject* CGlutWrapper2D::get_first_object (int ty)
{
	if (ty == -1) {
		return nullptr;
	}
	for (unsigned int i = 0; i < obj_array.size(); ++i) {
		if (obj_array[i]->get_type() == ty) {
			return obj_array[i];
		}
	}
	return reinterpret_cast<CObject*>(0);
}

void CGlutWrapper2D::tile_load_png (const char* filename, int tile_size)
{
#ifdef __GLUT_DEBUG
	stamp("\tLoading tiles as PNG.\n");
#endif
	unsigned long width, height;

	std::vector<byte> data;
	CPNGLoader::load_png(data, width, height, filename);
	width--;
	std::vector<gw::RGBAPixel> processed;
	gw::RGBAPixel pixel;
	unsigned int x = width;
	int y = -1;
	for (auto i = 0u; i < data.size(); ++i) {
		// workaround: x starts at 1
		if (x == width) {
			y++;
			x = 0;
		}
		else {
			x++;
		}
		pixel.x = (float)x;
		pixel.y = (float)y;
		pixel.red = data[i++];
		pixel.green = data[i++];
		pixel.blue = data[i++];
		pixel.alpha = data[i];
		processed.push_back(pixel);
	}
	std::reverse(processed.begin(), processed.end());
	for (auto i = 0u; i < processed.size(); ++i) {
		pixel = processed[i];
		if (!(pixel.red == 0 && pixel.green == 0 && pixel.blue == 0 && pixel.alpha == 0)) {
			tile_create(pixel.x * tile_size, pixel.y * tile_size, pixel.red, pixel.green, pixel.blue, pixel.alpha);
		}
	}
#ifdef __GLUT_DEBUG
	stamp("\tTiles loaded.\n");
#endif
}
void CGlutWrapper2D::room_load_bmp (const char* filename, int block_size)
{
#ifdef __GLUT_DEBUG
	stamp("\tLoading room from BMP.\n");
#endif
	std::vector<bmp::pixel> data;
	bmp::load_pixels(data, filename);

	for (auto i = 0u; i < data.size(); ++i) {
		if (data[i].red == 255) {
			continue;
		}
		try {
			instance_create(allocator(
				data[i].red), 
				data[i].x * block_size, 
				data[i].y * block_size);
		}
		catch (std::exception& e) {
			std::cerr << e.what() << "\ntype: " << (int)data[i].red << "\tx: " << (int)data[i].x << "\ty: " << (int)data[i].y << std::endl;
		}
	}
#ifdef __GLUT_DEBUG
	stamp("\tRoom loaded.\n");
#endif
}
void CGlutWrapper2D::room_load (const char* filename)
{
	std::vector<byte> data;
	gw::load_bin(data, filename);
	if (data[0] != 0x50 || data[1] != 0x47 || data[2] != 0x4C || data[3] != 0) {
		throw wrong_file();
	}
	byte p1 = 0;
	byte p2 = 0;
	unsigned short type = 0;
	unsigned short x = 0;
	unsigned short y = 0;

	for (unsigned int i = 4u; data[i] * 255 + data[i + 1] != 0xffff && i < data.size() - 4u; ++i) {
		p1 = data[i];
		p2 = data[++i];
		type = p1 * 255 + p2;
		
		p1 = data[++i];
		p2 = data[++i];
		x = p1 * 255 + p2;
		p1 = data[++i];
		p2 = data[++i];
		y = p1 * 255 + p2;
		
		instance_create(allocator(type), x, y);
	}
}

t_obj CGlutWrapper2D::instance_create (CObject* object)
{
	if (object == nullptr) {
		throw std::exception("Null pointer passed to instance_create\n(was an invalid type passed to allocator()?)");
	}
	object->_Allocated_new = true;
	return t_obj(obj_array.size() - 1);
}
t_obj CGlutWrapper2D::instance_create (CObject* object, double x, double y)
{
	instance_create(object);
	object->x = x;
	object->y = y;
	return t_obj(obj_array.size() - 1);
}
t_obj CGlutWrapper2D::instance_create (t_class object, double x, double y)
{
	instance_create(allocator(object));
	obj_array[obj_array.size()-1]->x = x;
	obj_array[obj_array.size()-1]->y = y;
	return t_obj(obj_array.size() - 1);
}
void CGlutWrapper2D::tile_create (float x, float y, float spr_x, float spr_y, float width, float height)
{
	tile_array.push_back(new CTile(x, y, spr_x, spr_y, width, height));
}
void CGlutWrapper2D::instance_destroy (const t_obj& object)
{
#ifdef __GLUT_DEBUG
	stamp("Destroying instance.\n");
#endif
	if (object > obj_array.size()) {
		throw std::out_of_range("Attempting to delete nonexistent object");
		return;
	}
	if (obj_array[object]->_Allocated_new == false) {
		throw std::out_of_range("Object not created by instance_create");
		return;
	}
	if (obj_array[object]->_Allocated_new) {
		delete obj_array[object];
	}
	obj_array.erase(obj_array.begin() + (t_obj_sz)object);

	for (t_obj_sz i = object; i < instance_ptrs.size(); ++i) {
		instance_ptrs[i]->operator--();
	}
#ifdef __GLUT_DEBUG
	stamp("Instance destroyed.\n");
#endif
}
void CGlutWrapper2D::instance_destroy (const CObject* object)
{
#ifdef __GLUT_DEBUG
	stamp("Destroying instance.\n");
#endif
	auto i = obj_array.begin();
	for (; i < obj_array.end(); ++i) {
		if (*i == object) {
			break;
		}
	}
	if (i == obj_array.end()) {
		throw std::out_of_range("Object pointer not found");
	}
	t_obj_ptr obj = *i;
	if (obj->_Allocated_new) {
		delete obj;
	}
	objs--;
	obj_array.erase(i);
#ifdef __GLUT_DEBUG
	stamp("Instance destroyed.\n");
#endif
}
void CGlutWrapper2D::instance_destroy (const CObject& object)
{
	instance_destroy(&object);
}

t_view CGlutWrapper2D::new_view (const CViewInfo& view)
{
	views.push_back(view);
	return views.size() - 1;
}
void CGlutWrapper2D::remove_view (t_view view)
{
	// messy; erase requires const_iterator
	views.erase(std::vector<CViewInfo>::const_iterator(views.begin() + view));
}
void CGlutWrapper2D::modify_view (const CViewInfo& replacement, t_view view)
{
	views[view] = replacement;
}
void CGlutWrapper2D::set_view (t_view view)
{
	current_view = view;
}

_Inline
CGlutWrapper2D::CGlutWrapper2D ():
	objs(0), window_width(1440), window_height(900), window_x(-1), window_y(-1), window_name("2D Rendering"),
	clear_col(COLOUR(0, 0, 0)), render_count(0), ended(false)
{ construct(); }
_Inline
CGlutWrapper2D::CGlutWrapper2D (const int w, const int h, const int x, const int y, const char* name, COLOUR c):
objs(0), window_width(w), window_height(h), window_x(x), window_y(y), window_name(name), clear_col(c), render_count(0), ended(false)
{ construct(); }
CGlutWrapper2D::~CGlutWrapper2D ()
{
	for (unsigned int i = 0; i < obj_array.size(); ++i) {
		if (obj_array[i] != nullptr && obj_array[i]->_Allocated_new) {
			delete obj_array[i];
		}
	}
#ifdef __GLUT_DEBUG
		_CrtDumpMemoryLeaks();
#endif
}

void CGlutWrapper2D::begin (int& argc, char**& argv) const
{
#ifdef __GLUT_DEBUG
	stamp("Initialising GLUT...\n");
#endif
	glutInit(&argc, argv);

#ifdef __GLUT_DEBUG
	stamp("GLUT initialised.\n");
#endif

#ifdef __GLUT_DEBUG
	stamp("Setting window up...\n");
#endif;
	glutInitDisplayMode(GLUT_DOUBLE | GLUT_DEPTH | GLUT_RGBA | GLUT_ALPHA);
	glutInitWindowPosition(window_x, window_y);
	glutInitWindowSize(window_width, window_height);
#ifdef __GLUT_DEBUG
	stamp("Window set up. Creating window...\n");
#endif
	glutCreateWindow(window_name);
#ifdef __GLUT_DEBUG
	stamp("Fullscreening...\n");
#endif
	glutFullScreen();
#ifdef __GLUT_DEBUG
	stamp("Fullscreen. Setting up callbacks...\n");
#endif

	glutDisplayFunc(CGlutWrapper2D::callback::display);
	glutIdleFunc(CGlutWrapper2D::callback::display);
	glutReshapeFunc(CGlutWrapper2D::callback::reshape);
	glutKeyboardFunc(CGlutWrapper2D::callback::dkey);
	glutKeyboardUpFunc(CGlutWrapper2D::callback::ukey);
	glutSpecialFunc(CGlutWrapper2D::callback::dkeyx);
	glutSpecialUpFunc(CGlutWrapper2D::callback::ukeyx);
#ifdef __GLUT_DEBUG
	stamp("Callbacks done. Misc GL options...\n");
#endif

	glutIgnoreKeyRepeat(1);
	glutSetCursor(GLUT_CURSOR_NONE);

	glEnable(GL_TEXTURE_2D);
	glEnable(GL_DEPTH_TEST);
	glEnable(GL_BLEND);
	glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

	glShadeModel(GL_SMOOTH);

	glClearColor(0.0f, 0.0f, 0.0f, 0.5f);
	glClearDepth(1.0f);
	
	glDepthFunc(GL_LEQUAL);
	glHint(GL_PERSPECTIVE_CORRECTION_HINT, GL_NICEST);
#ifdef __GLUT_DEBUG
	stamp("Setup finished.\n");
#endif
#ifdef __GLUT_DEBUG
	std::cout << "\nLoading complete; took " << (double)gw::ticks() / 1000000.0 << " seconds.\n\n";
#endif
}
void CGlutWrapper2D::begin (int& argc, char**& argv, char* room_file, char* tile_file, int tile_size) const
{
#ifdef __GLUT_DEBUG
	stamp("Initialising GLUT...\n");
#endif
	glutInit(&argc, argv);
#ifdef __GLUT_DEBUG
	stamp("GLUT initialised.\n");
#endif

#ifdef __GLUT_DEBUG
	stamp("Setting window up...\n");
#endif;
	glutInitDisplayMode(GLUT_DOUBLE | GLUT_DEPTH | GLUT_RGBA | GLUT_ALPHA);
	glutInitWindowPosition(window_x, window_y);
	glutInitWindowSize(window_width, window_height);
#ifdef __GLUT_DEBUG
	stamp("Window set up. Creating window...\n");
#endif
	glutCreateWindow(window_name);
	/*HANDLE threads[3];*/
	/*stamp_if("Creating window worker thread...\n");
	threads[0] = (HANDLE)_beginthread(_t_makewin, 0, (void*)window_name);*/
	/*stamp_if("Creating room worker thread...\n");
	char* args[2];
	args[0] = room_file;
	char tsz = (char)tile_size;
	args[1] = &tsz;
	threads[0] = (HANDLE)_beginthread(_t_loadroom, 0, (void*)args);
	stamp_if("Creating tile worker thread...\n");
	args[0] = tile_file;
	threads[1] = (HANDLE)_beginthread(_t_loadtiles, 0, (void*)args);
	stamp("Waiting for worker threads...\n");	
	WaitForMultipleObjects(2, threads, true, INFINITE);*/
#ifdef __GLUT_DEBUG
	stamp("Fullscreening...\n");
#endif
	glutFullScreen();
#ifdef __GLUT_DEBUG
	stamp("Fullscreen. Setting up callbacks...\n");
#endif

	glutDisplayFunc(CGlutWrapper2D::callback::display);
	glutIdleFunc(CGlutWrapper2D::callback::display);
	glutReshapeFunc(CGlutWrapper2D::callback::reshape);
	glutKeyboardFunc(CGlutWrapper2D::callback::dkey);
	glutKeyboardUpFunc(CGlutWrapper2D::callback::ukey);
	glutSpecialFunc(CGlutWrapper2D::callback::dkeyx);
	glutSpecialUpFunc(CGlutWrapper2D::callback::ukeyx);
#ifdef __GLUT_DEBUG
	stamp("Callbacks done. Misc GL options...\n");
#endif

	glutIgnoreKeyRepeat(1);
	glutSetCursor(GLUT_CURSOR_NONE);

	glEnable(GL_TEXTURE_2D);
	glEnable(GL_DEPTH_TEST);
	glEnable(GL_BLEND);
	glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

	glShadeModel(GL_SMOOTH);

	glClearColor(0.0f, 0.0f, 0.0f, 0.5f);
	glClearDepth(1.0f);
	
	glDepthFunc(GL_LEQUAL);
	glHint(GL_PERSPECTIVE_CORRECTION_HINT, GL_NICEST);
#ifdef __GLUT_DEBUG
	stamp("Setup finished.\n");
	std::cout << "\nLoading complete; took " << (double)gw::ticks() / 1000000.0 << " seconds.\n\n";
#endif
}

void CSprite::assign (float xpos, float ypos, float width, float height)
{
	load(xpos, ypos, width, height);
}
inline
void CSprite::load (float xpos, float ypos, float ww, float hh)
{
	dead = false;

	if (width == 0) {
		throw std::runtime_error("Texture must be assigned before loading sprite");
	}

	x = xpos;
	y = ypos;
	w = ww;
	h = hh;

	tv.clear();
	tv.push_back(x / width, (y + h) / height);
	tv.push_back((x + w) / width, (y + h) / height);
	tv.push_back((x + w) / width, y / height);
	tv.push_back(x / width, y / height);
}
void CSprite::set_texture (const char* filename, t_load load_func)
{
	stamp_if("Setting sprite texture. Filename:\n");
	std::stringstream fname_new;
	fname_new << filename << "\n";
	stamp_if(fname_new.str().c_str());
	glColor4f(1.0, 1.0, 1.0, 1.0);

	std::vector<byte> image;

	unsigned long _width, _height;

	load_func(image, _width, _height, filename);
	glGenTextures(1, &single_texture);
	glBindTexture(GL_TEXTURE_2D, single_texture);
	glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, _width, _height, 0, GL_RGBA, GL_UNSIGNED_BYTE, &(image.front()));

	glTexEnvf(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_MODULATE);

	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);

	CSprite::width = _width;
	CSprite::height = _height;
	stamp_if("Texture set.\n");
}

void CSprite::draw (float xpos, float ypos, double xspeed, double yspeed)
{
	CGlutWrapper2D::get_ptr().draw_rectangle(xpos, ypos, (float)w, (float)h, WHITE, tv, xspeed, yspeed);
}

CAnimation::CAnimation ():
	x(-1), y(-1), w(-1), h(-1), current_image(0), img_speed(0), dead(true)
{
	n_images = gw::make_pair(0, 0);
}
CAnimation::CAnimation (float xpos, float ypos, float total_width, float total_height, int images_x, int images_y):
	x(xpos), y(ypos), w(total_width), h(total_height), img_speed(0)
{
	assign(x, y, w, h, images_x, images_y);
}
CAnimation::CAnimation (float xpos, float ypos, float total_width, float total_height, int images_x, int images_y, double image_speed):
	x(xpos), y(ypos), w(total_width), h(total_height), img_speed(image_speed)
{
	assign(x, y, w, h, images_x, images_y);
}
CAnimation::~CAnimation ()
{
	for (GLuint i = 0; i < sprite_vector.size(); ++i) {
		delete sprite_vector[i];
	}
}
void CAnimation::assign (float xpos, float ypos, float total_width, float total_height, int images_x, int images_y)
{
	dead = false;

	n_images = gw::make_pair(images_x, images_y);

	x = xpos;
	y = ypos;
	w = total_width;
	h = total_height;

	float ww = total_width / images_x;
	float hh = total_height / images_y;

	if (!sprite_vector.empty()) {
		for (unsigned int i = 0; i < sprite_vector.size(); ++i) {
			delete sprite_vector[i];
		}
		sprite_vector.clear();
	}

	for(int i = 0; i < images_x; ++i) {
		for (int ii = 0; ii < images_y; ++ii) {
			sprite_vector.push_back(new CSprite());
			sprite_vector[i]->assign(x + ww * i, y + hh * ii, ww, hh);
		}
	}
}
void CAnimation::draw (float xpos, float ypos, double xspeed, double yspeed)
{
	if (dead) return;
	CGlutWrapper2D::get_ptr().draw_rectangle
		(xpos, ypos, w / n_images.first, h / n_images.second, WHITE, sprite_vector[(int)std::floor(current_image)]->tv,
		xspeed, yspeed);
}
void CAnimation::step ()
{
	current_image += img_speed * gw::dt();
	if (current_image >= n_images.get_total()) {
		current_image = 0;
	}
}

void CObject::default_step()
{
	collision_sprite.draw((float)x, (float)y);
}
bool CObject::collides_with (int object) const
{
	bool ret = false;
	t_array_return v = glut.get_obj_list(object);
	for (unsigned int i = 0; i < v.size(); ++i) {
		if (collides_with(v[i])) {
			ret = true;
		}
	}

	return ret;
}
bool CObject::collides_with (const CObject* with) const
{
	return collides_with(*with);
}
bool CObject::collides_with (const CObject& with) const
{
	if (this == &with) {
		return false;
	}
	/*if (with.collision_sprite.is_dead() || collision_sprite.is_dead()) {
		return false;
	}*/

	double l1, l2;
	double r1, r2;
	double u1, u2;
	double d1, d2;

	//const float offs = collision_sprite.get_offset();
	//const float woffs = with.collision_sprite.get_offset();
	const double wid = get_width();
	const double wwid = with.get_width();
	const double hei = get_height();
	const double whei = with.get_height();

	l1 = x;
	l2 = with.x;

	r1 = x + wid;
	r2 = with.x + wwid;

	u1 = y;
	u2 = with.y;

	d1 = y + hei;
	d2 = with.y + whei;

	if (d1 < u2) return false;
	if (u1 > d2) return false;

	if (r1 < l2) return false;
	if (l1 > r2) return false;

	return true;
}
bool CObject::collides_with (const gw::CRange& objects) const
{
	bool ret = false;
	t_array_return v = glut.get_obj_list(objects);
	for (unsigned int i = 0; i < v.size(); ++i) {
		if (collides_with(v[i])) {
			ret = true;
		}
	}

	return ret;
}
t_obj_ptr CObject::get_collision (int object) const
{
	t_array_return v = glut.get_obj_list(object);
	for (unsigned int i = 0; i < v.size(); ++i) {
		if (collides_with(v[i])) {
			return v[i];
		}
	}
	return noone;
}
bool CObject::collides () const
{
	bool ret = false;
	t_array_return v = glut.get_obj_list();
	for (unsigned int i = 0; i < v.size(); ++i) {
		if (collides_with(v[i])) {
			ret = true;
		}
	}

	return ret;
}
bool CObject::place_free (double x, double y) const
{
	return glut._area_free(this, x, y, x + get_width(), y + get_height());
}
bool CObject::place_meeting (double x, double y, ty_obj object) const
{
	if (!place_free(x, y)) {
		double ox1;
		double oy1;
		double ox2;
		double oy2;
		double x2 = x + get_width();
		double y2 = y + get_height();
		const CObject* ptr = nullptr;
		t_array_return v = glut.get_obj_list(object);
		
		for (unsigned int i = 0; i < v.size(); ++i) {
			ptr = v[i];
			if (ptr != this) {
				if (ptr == nullptr) {
					throw std::bad_alloc("Null pointer in object list!");
				}
				ox1 = ptr->get_x();
				oy1 = ptr->get_y();
				ox2 = ox1 + ptr->get_width();
				oy2 = oy1 + ptr->get_height();
				if (!(y2 < oy1 || y > oy2 || x2 < ox1 || x > ox2)) {
					return true;
				}
			}
		}
	}
	return false;
}
bool CObject::place_meeting (double x, double y, const gw::CRange& objects) const
{
	if (!place_free(x, y)) {
		double ox1;
		double oy1;
		double ox2;
		double oy2;
		double x2 = x + get_width();
		double y2 = y + get_height();
		const CObject* ptr = nullptr;
		t_array_return v = glut.get_obj_list(objects);
		
		for (unsigned int i = 0; i < v.size(); ++i) {
			ptr = v[i];
			if (ptr != this) {
				if (ptr == nullptr) {
					throw std::bad_alloc("Null pointer in object list!");
				}
				ox1 = ptr->get_x();
				oy1 = ptr->get_y();
				ox2 = ox1 + ptr->get_width();
				oy2 = oy1 + ptr->get_height();
				if (!(y2 < oy1 || y > oy2 || x2 < ox1 || x > ox2)) {
					return true;
				}
			}
		}
	}
	return false;
}
		
void CObject::die () const
{
	glut.instance_destroy(this);
}

namespace gw {
	long long ticks ()
	{
		static LARGE_INTEGER start;
		static bool setup = false;
		if (!setup) {
			QueryPerformanceCounter(&start);
			setup = true;
		}
		LARGE_INTEGER ret;
		QueryPerformanceCounter(&ret);
		return ret.QuadPart - start.QuadPart;
	}
	int get_width () { return GetSystemMetrics(SM_CXSCREEN); }
	int get_height () { return GetSystemMetrics(SM_CYSCREEN); }
	CVector::CVector ():
		x(0), y(0)
	{}
	void clamp_d (double& target, double a, double b)
	{
		if (target < a) {
			target = a;
		}
		else if (target > b) {
			target = b;
		}
	}
	CRange::CRange ():
		val1(0), val2(0)
		{}
	CRange::CRange (int v1):
		val1(v1), val2(v1)
		{}
	CRange::CRange (int v1, int v2):
		val1(v1), val2(v2)
		{}

	void draw_text (const std::string& text, t_font font, float x, float y,  COLOUR col, float depth)
	{
		CGlutWrapper2D& device = CGlutWrapper2D::get_ptr();
		device.text_stack.push(text);

		device.render_stack.push((float)(int)font);
		device.render_stack.push(x);
		device.render_stack.push(y);
		device.render_stack.push(col.red);
		device.render_stack.push(col.green);
		device.render_stack.push(col.blue);
		device.render_stack.push(depth);
		device.render_stack.push(GL_TEXT);
		device.render_stack.push(WHITE.blue);
		device.render_stack.push(WHITE.green);
		device.render_stack.push(WHITE.red);
	}
	void draw_number (int number, t_font font, float x, float y, COLOUR col, float depth)
	{
		std::stringstream n;
		n << number;
		draw_text(n.str(), font, x, y, col, depth);
	}
	void foreach_obj (CGlutWrapper2D& glut, int type, void (*lambda)(t_lambda_arg*))
	{
		t_array_return arr = glut.get_obj_list(type);
		std::for_each(arr.begin(), arr.end(), lambda);
	}

	void load_bin (std::vector<unsigned char>& buffer, const std::string& filename) //designed for loading files from hard disk in an std::vector
	{
		std::ifstream file(filename.c_str(), std::ios::in|std::ios::binary|std::ios::ate);

		  std::streamsize size = 0;
		  if(file.seekg(0, std::ios::end).good()) size = file.tellg();
		  if(file.seekg(0, std::ios::beg).good()) size -= file.tellg();

		  if(size > 0) {
				buffer.resize((size_t)size);
				file.read((char*)(&buffer[0]), size);
		  }
		  else buffer.clear();
	}	
}
