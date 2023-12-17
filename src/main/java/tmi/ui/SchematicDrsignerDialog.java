package tmi.ui;

import arc.Core;
import arc.Graphics;
import arc.func.Cons;
import arc.func.Func;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.gl.FrameBuffer;
import arc.input.KeyCode;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Geometry;
import arc.math.geom.Point2;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.actions.Actions;
import arc.scene.event.*;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.*;
import arc.util.*;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.core.UI;
import mindustry.gen.Icon;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.meta.StatUnit;
import tmi.TooManyItems;
import tmi.recipe.EnvParameter;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeItemStack;
import tmi.recipe.RecipeType;
import tmi.recipe.types.GeneratorRecipe;
import tmi.recipe.types.RecipeItem;
import tmi.util.Consts;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Iterator;

import static arc.util.Align.*;
import static mindustry.Vars.mobile;

public class SchematicDrsignerDialog extends BaseDialog {
  private static final Seq<ItemLinker> seq = new Seq<>();
  private static final Vec2 tmp = new Vec2();
  private static final Rect rect = new Rect();
  protected final View view;

  protected final Table menuTable = new Table(){{
    visible = false;
  }};
  protected OrderedSet<Card> selects = new OrderedSet<>();

  protected Table removeArea, sideTools;
  protected boolean editLock, removeMode, selectMode;

  public SchematicDrsignerDialog() {
    super("");

    titleTable.clear();

    cont.table().grow().get().add(view = new View()).grow();

    addChild(menuTable);

    hidden(() -> {
      removeMode = false;
      removeArea.setHeight(0);
      removeArea.color.a = 0;
      selectMode = false;
      editLock = false;
      hideMenu();
    });
    fill(t -> {
      t.bottom().table(Consts.darkGrayUI, area -> {
        removeArea = area;
        area.color.a = 0;
        area.add(Core.bundle.get("dialog.calculator.removeArea"));
      }).bottom().growX().height(0);
    });

    fill(t -> {
      t.top().table(zoom -> {
        zoom.add("25%").color(Color.gray);
        zoom.table(Consts.darkGrayUIAlpha).fill().get().slider(0.25f, 1f, 0.01f, f -> {
          view.zoom.setScale(f);
          view.zoom.setOrigin(Align.center);
          view.zoom.setTransform(true);
        }).update(s -> s.setValue(view.zoom.scaleX)).width(400);
        zoom.add("100%").color(Color.gray);
      }).top();
    });

    fill(t -> {
      t.left().table(Consts.darkGrayUI, sideBar -> {
        sideTools = sideBar;
        sideBar.top().defaults().size(40).padBottom(8);

        sideBar.button(Icon.add, Styles.clearNonei, 32, () -> {
          TooManyItems.recipesDialog.toggle = r -> {
            TooManyItems.recipesDialog.hide();
            addRecipe(r);
          };
          TooManyItems.recipesDialog.show();
        });
        sideBar.row();

        sideBar.button(Icon.refresh, Styles.clearNonei, 32, view::standardization);
        sideBar.row();

        sideBar.button(Icon.resize, Styles.clearNoneTogglei, 32, () -> selectMode = !selectMode)
            .update(b -> b.setChecked(selectMode));
        sideBar.row();

        sideBar.button(Icon.download, Styles.clearNonei, 32, () -> {
          view.read(new Reads(new DataInputStream(Vars.modDirectory.child("test.b").read())));
        });
        sideBar.row();

        sideBar.button(Icon.save, Styles.clearNonei, 32, () -> {
          view.write(new Writes(new DataOutputStream(Vars.modDirectory.child("test.b").write())));
        });
        sideBar.row();

        sideBar.button(Icon.export, Styles.clearNonei, 32, () -> {

        });
        sideBar.row();

        sideBar.button(Icon.trash, Styles.clearNoneTogglei, 32, () -> {
          removeMode = !removeMode;

          removeArea.clearActions();
          if (removeMode) {
            removeArea.actions(Actions.parallel(Actions.sizeTo(removeArea.getWidth(), Core.scene.getHeight()*0.15f, 0.12f), Actions.alpha(0.6f, 0.12f)));
          }
          else removeArea.actions(Actions.parallel(Actions.sizeTo(removeArea.getWidth(), 0, 0.12f), Actions.alpha(0, 0.12f)));
        }).update(b -> b.setChecked(removeMode));
        sideBar.row();

        sideBar.button(Icon.lock, Styles.clearNoneTogglei, 32, () -> {
          editLock = !editLock;
        }).update(b -> {
          b.setChecked(editLock);
          b.getStyle().imageUp = editLock? Icon.lock: Icon.lockOpen;
        });
        sideBar.row();

        sideBar.add().growY();

        sideBar.row();
        sideBar.button(Icon.infoCircle, Styles.clearNonei, 32, () -> {

        }).padBottom(0);
        sideBar.row();
      }).left().growY().fillX().padTop(100).padBottom(100);
    });
  }

  public void addRecipe(Recipe recipe){
    view.addCard(new RecipeCard(recipe));
  }

  public void addIO(RecipeItem<?> item, boolean isInput) {
    view.addCard(new IOCard(item, isInput));
  }

  public void moveLock(boolean lock){
    view.lock = lock;
  }
  public void selectLock(boolean lock){
    view.selectLoc = lock;
  }

  public void showMenu(Cons<Table> tabBuilder, Element showOn, int alignment, int tableAlign, boolean pack){
    menuTable.clear();
    tabBuilder.get(menuTable);
    menuTable.draw();
    menuTable.act(1);
    if (pack) menuTable.pack();

    menuTable.visible = true;

    Vec2 v = new Vec2();
    Runnable r;
    menuTable.update(r = () -> {
      if (pack) menuTable.pack();
      v.set(showOn.x, showOn.y);

      if((alignment & right) != 0)
        v.x += showOn.getWidth();
      else if((alignment & left) == 0)
        v.x += showOn.getWidth() / 2;

      if((alignment & top) != 0)
        v.y += showOn.getHeight();
      else if((alignment & bottom) == 0)
        v.y += showOn.getHeight() / 2;

      showOn.parent.localToAscendantCoordinates(this, v);
      menuTable.setPosition(v.x, v.y, tableAlign);
    });

    r.run();
  }

  public void hideMenu(){
    menuTable.visible = false;
  }

  protected class View extends Group{
    final Seq<Card> cards = new Seq<>();
    final Vec2 selectBegin = new Vec2(), selectEnd = new Vec2();
    boolean isSelecting;

    Card newSet;
    boolean lock;
    boolean selectLoc;

    float lastZoom = -1;
    float panX, panY;

    final Group container, zoom;

    View(){
      zoom = new Group(){
        {
          setFillParent(true);
          setTransform(true);
        }

        @Override
        public void draw() {
          validate();
          super.draw();
        }
      };
      container = new Group() {
        @Override
        public void act(float delta) {
          super.act(delta);

          setPosition(panX + parent.getWidth() / 2f, panY + parent.getHeight() / 2f, Align.center);
        }

        @Override
        public void draw() {
          Consts.grayUI.draw(-Core.scene.getWidth()/zoom.scaleX, -Core.scene.getHeight()/zoom.scaleY, Core.scene.getWidth()/zoom.scaleX*2, Core.scene.getHeight()/zoom.scaleY*2);

          Lines.stroke(Scl.scl(4), Pal.gray);
          Draw.alpha(parentAlpha);
          for (float offX = 0; offX <= (Core.scene.getWidth())/zoom.scaleX - panX; offX += 150){
            Lines.line(x + offX, -Core.scene.getHeight()/zoom.scaleY, x + offX, Core.scene.getHeight()/zoom.scaleY*2);
          }
          for (float offX = 0; offX >= -(Core.scene.getWidth())/zoom.scaleX - panX; offX -= 150){
            Lines.line(x + offX, -Core.scene.getHeight()/zoom.scaleY, x + offX, Core.scene.getHeight()/zoom.scaleY*2);
          }

          for (float offY = 0; offY <= (Core.scene.getHeight())/zoom.scaleY - panY; offY += 150){
            Lines.line(-Core.scene.getWidth()/zoom.scaleX, y + offY, Core.scene.getWidth()/zoom.scaleX*2, y + offY);
          }
          for (float offY = 0; offY >= -(Core.scene.getHeight())/zoom.scaleY - panY; offY -= 150){
            Lines.line(-Core.scene.getWidth()/zoom.scaleX, y + offY, Core.scene.getWidth()/zoom.scaleX*2, y + offY);
          }
          super.draw();
        }
      };
      zoom.addChild(container);
      fill(t -> t.add(zoom).grow());
      fill(buttons -> {
        buttons.bottom().button("@back", Icon.left, SchematicDrsignerDialog.this::hide).size(210, 64f);
        buttons.bottom().button("shoot", Icon.play, () -> {
          TextureRegion region = toImage(20, 20, 1);
          PixmapIO.writePng(Vars.modDirectory.child("target.png"), region.texture.getTextureData().getPixmap());
        }).size(210, 64f);

        addCloseListener();
      });

      addListener(new InputListener(){
        @Override
        public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY){
          zoom.setScale(lastZoom = Mathf.clamp(zoom.scaleX - amountY / 10f * zoom.scaleX, 0.25f, 1));
          zoom.setOrigin(Align.center);
          zoom.setTransform(true);

          clamp();
          return true;
        }

        @Override
        public void enter(InputEvent event, float x, float y, int pointer, Element fromActor) {
          requestScroll();
          super.enter(event, x, y, pointer, fromActor);
        }
      });

      addCaptureListener(new InputListener(){
        boolean enabled, shown;
        float timer;

        @Override
        public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
          if (shown){
            hideMenu();
            shown = false;
          }
          if (!(enabled = pointer == 0 && (button == KeyCode.mouseRight || !useKeyboard()))) return false;
          timer = Time.globalTime;
          return true;
        }

        @Override
        public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
          if (pointer != 0 || !((useKeyboard() && button == KeyCode.mouseRight) || (!useKeyboard() && Time.globalTime - timer > 60))) return;
          if (enabled){
            Card selecting = hitCard(x, y, true);
            if (selecting != null){
              selects.add(selecting);
              selectLock(true);
            }

            shown = true;
            showMenu(tab -> {
              tab.table(Consts.darkGrayUIAlpha, menu -> {
                menu.defaults().growX().fillY().minWidth(300);

                if (selecting != null){
                  menu.button(Core.bundle.get("misc.remove"), Icon.trash, Styles.cleart, 22, () -> {
                    hideMenu();
                    selects.each(View.this::removeCard);
                  }).margin(12).get().getLabelCell().padLeft(6).get().setAlignment(left);
                  menu.row();

                  menu.button(Core.bundle.get("misc.copy"), Icon.copy, Styles.cleart, 22, () -> {
                    hideMenu();
                    for (Card card : selects) {
                      Card clone = card.copy();
                      tmp.set(clone.x + clone.getWidth()/2 + 40, clone.y + clone.getHeight()/2 - 40);
                      container.localToStageCoordinates(tmp);
                      addCard(clone, tmp.x, tmp.y, true);
                    }
                  }).margin(12).get().getLabelCell().padLeft(6).get().setAlignment(left);
                  menu.row();

                  menu.image().height(4).pad(4).padLeft(0).padRight(0).growX().color(Color.lightGray);
                  menu.row();
                }

                menu.button(Core.bundle.get("dialog.calculator.addRecipe"), Icon.book, Styles.cleart, 22, () -> {
                  TooManyItems.recipesDialog.toggle = r -> {
                    TooManyItems.recipesDialog.hide();
                    addRecipe(r);

                    tmp.set(x, y);
                    container.stageToLocalCoordinates(tmp);
                    newSet.setPosition(tmp.x, tmp.y, center);
                  };
                  TooManyItems.recipesDialog.show();
                  hideMenu();
                }).margin(12).get().getLabelCell().padLeft(6).get().setAlignment(left);
                menu.row();
                menu.button(Core.bundle.get("dialog.calculator.addInput"), Icon.download, Styles.cleart, 22, () -> {
                  addCard(new IOCard(TooManyItems.itemsManager.getItem(Items.surgeAlloy), true), x, y, true);
                }).margin(12).get().getLabelCell().padLeft(6).get().setAlignment(left);
                menu.row();
                menu.button(Core.bundle.get("dialog.calculator.addOutput"), Icon.upload, Styles.cleart, 22, () -> {
                  addCard(new IOCard(TooManyItems.itemsManager.getItem(Items.surgeAlloy), false), x, y, true);
                }).margin(12).get().getLabelCell().padLeft(6).get().setAlignment(left);
              }).update(t -> {
                int align = 0;

                if (x + t.getWidth() > tab.getWidth()) align |= right;
                else align |= left;
                if (y - t.getHeight() < 0) align |= bottom;
                else align |= top;

                t.setPosition(x, y, align);
              });

              tab.setSize(view.width, view.height);
            }, view, bottomLeft, bottomLeft, false);
          }

          enabled = false;
        }

        @Override
        public void touchDragged(InputEvent event, float x, float y, int pointer) {
          if (pointer != 0) return;
          if (Mathf.dst(x, y) > 12) enabled = false;
        }
      });

      addCaptureListener(new ElementGestureListener(){
        boolean panEnable;

        @Override
        public void zoom(InputEvent event, float initialDistance, float distance){
          if(lastZoom < 0){
            lastZoom = zoom.scaleX;
          }

          zoom.setScale(Mathf.clamp(distance / initialDistance * lastZoom, 0.25f, 1));
          zoom.setOrigin(Align.center);
          zoom.setTransform(true);

          clamp();
        }

        @Override
        public void touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
          if (button != KeyCode.mouseLeft || pointer != 0) return;
          panEnable = true;
        }

        @Override
        public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button){
          if (button != KeyCode.mouseLeft || pointer != 0) return;
          lastZoom = zoom.scaleX;
          panEnable = false;
        }

        @Override
        public void pan(InputEvent event, float x, float y, float deltaX, float deltaY){
          if (!panEnable || lock) return;

          panX += deltaX/zoom.scaleX;
          panY += deltaY/zoom.scaleY;
          selectLock(true);
          clamp();
        }
      });

      addCaptureListener(new ElementGestureListener(){
        boolean enable, panned;
        float beginX, beginY;

        final Rect rect = new Rect();

        @Override
        public void touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
          enable = selectMode || button == KeyCode.mouseRight;
          if (enable){
            moveLock(true);

            beginX = x;
            beginY = y;

            selectBegin.set(selectEnd.set(x, y));
            isSelecting = true;
          }
        }

        @Override
        public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
          if (!panned && !selectLoc) selects.clear();
          selectLoc = false;
          if (selectMode || button == KeyCode.mouseRight) {
            moveLock(false);
            enable = false;
            isSelecting = false;
            panned = false;
          }
        }

        @Override
        public void pan(InputEvent event, float x, float y, float deltaX, float deltaY) {
          if (enable){
            if (!panned && Mathf.dst(x - beginX, y - beginY) > 12){
              panned = true;
            }

            selectEnd.set(x, y);
            tmp.set(selectBegin);
            localToStageCoordinates(tmp);
            rect.setPosition(tmp.x, tmp.y);
            tmp.set(selectEnd);
            localToStageCoordinates(tmp);
            rect.setSize(tmp.x - rect.x, tmp.y - rect.y);

            if (rect.width < 0){
              rect.set(rect.x + rect.width, rect.y, -rect.width, rect.height);
            }
            if (rect.height < 0){
              rect.set(rect.x, rect.y + rect.height, rect.width, -rect.height);
            }

            if (panned){
              if (!selectMode && !Core.input.keyDown(TooManyItems.binds.hotKey)) selects.clear();
              eachCard(rect, selects::add, true);
            }
          }
        }
      });
    }

    @Override
    public void draw() {
      super.draw();
      if (isSelecting){
        Draw.color(Pal.accent, 0.35f*parentAlpha);
        Fill.rect(x + selectBegin.x + (selectEnd.x - selectBegin.x)/2, y + selectBegin.y + (selectEnd.y - selectBegin.y)/2,
            selectEnd.x - selectBegin.x, selectEnd.y - selectBegin.y);
      }
    }

    public void addCard(Card card){
      addCard(card, Core.scene.getWidth()/2, Core.scene.getHeight()/2, true);
    }

    public void addCard(Card card, boolean build){
      addCard(card, Core.scene.getWidth()/2, Core.scene.getHeight()/2, build);
    }

    public void addCard(Card card, float x, float y, boolean build){
      cards.add(card);
      container.addChild(card);

      tmp.set(x, y);
      container.stageToLocalCoordinates(tmp);

      if (build) {
        card.build();
        card.buildLinker();
        card.draw();
        card.setPosition(tmp.x, tmp.y, Align.center);
      }
      newSet = card;
    }

    public void removeCard(Card card){
      cards.remove(card);
      container.removeChild(card);

      seq.clear().addAll(card.in).addAll(card.out);
      for (ItemLinker linker : seq) {
        for (ItemLinker link : linker.links.keys().toSeq()) {
          linker.deLink(link);
          if (link.links.isEmpty() && link.isInput) link.remove();
        }
      }
    }

    private void clamp() {
      Group par = parent;
      if (par == null) return;
    }

    void eachCard(Rect range, Cons<Card> cons, boolean inner){
      for (Card card : cards) {
        if (inner) tmp.set(card.child.x, card.child.y).add(card.x, card.y);
        else tmp.set(card.x, card.y);
        card.parent.localToStageCoordinates(tmp);
        float ox = tmp.x;
        float oy = tmp.y;

        if (inner) tmp.set(card.child.x + card.child.getWidth(), card.child.y + card.child.getHeight()).add(card.x, card.y);
        else tmp.set(card.x + card.getWidth(), card.y + card.getHeight());
        card.parent.localToStageCoordinates(tmp);
        float wx = tmp.x;
        float wy = tmp.y;

        rect.set(ox, oy, wx - ox, wy - oy);
        if (range.overlaps(rect)) cons.get(card);
      }
    }

    Card hitCard(float stageX, float stageY, boolean inner){
      for (int s = cards.size - 1; s >= 0; s--) {
        Card card = cards.get(s);

        if (inner) tmp.set(card.child.x, card.child.y).add(card.x, card.y);
        else tmp.set(card.x, card.y);
        card.parent.localToStageCoordinates(tmp);
        float ox = tmp.x;
        float oy = tmp.y;

        if (inner) tmp.set(card.child.x + card.child.getWidth(), card.child.y + card.child.getHeight()).add(card.x, card.y);
        else tmp.set(card.x + card.getWidth(), card.y + card.getHeight());
        card.parent.localToStageCoordinates(tmp);
        float wx = tmp.x;
        float wy = tmp.y;

        if (ox < stageX && stageX < wx && oy < stageY && stageY < wy) {
          return card;
        }
      }

      return null;
    }

    public void standardization(){
      Vec2 v1 = new Vec2(Float.MAX_VALUE, Float.MAX_VALUE);
      Vec2 v2 = v1.cpy().scl(-1);

      for (Card card : cards) {
        v1.x = Math.min(v1.x, card.x);
        v1.y = Math.min(v1.y, card.y);

        v2.x = Math.max(v2.x, card.x + card.getWidth());
        v2.y = Math.max(v2.y, card.y + card.getHeight());
      }

      v2.add(v1).scl(0.5f);

      float offX = -v2.x - container.getWidth()/2;
      float offY = -v2.y - container.getHeight()/2;

      for (Card card : cards) {
        card.moveBy(offX, offY);
      }

      panX = 0;
      panY = 0;
    }

    public TextureRegion toImage(float boundX, float boundY, float scl){
      Vec2 v1 = new Vec2(Float.MAX_VALUE, Float.MAX_VALUE);
      Vec2 v2 = v1.cpy().scl(-1);
      for (Card card : cards) {
        v1.x = Math.min(v1.x, card.x);
        v1.y = Math.min(v1.y, card.y);

        v2.x = Math.max(v2.x, card.x + card.getWidth());
        v2.y = Math.max(v2.y, card.y + card.getHeight());
      }

      float width = v2.x - v1.x + boundX*2;
      float height = v2.y - v1.y + boundY*2;

      float dx = v1.x - boundX;
      float dy = v1.y - boundY;

      Camera camera = new Camera();
      camera.width = width;
      camera.height = height;
      camera.position.x = dx + width/2f;
      camera.position.y = dy + height/2f;
      camera.update();

      Group par = container.parent;
      float x = container.x;
      float y = container.y;
      float px = panX;
      float py = panY;
      float sclX = zoom.scaleX;
      float sclY = zoom.scaleY;
      float scW = Core.scene.getWidth();
      float scH = Core.scene.getHeight();

      zoom.scaleX = 1;
      zoom.scaleY = 1;
      panX = 0;
      panY = 0;
      container.parent = null;
      container.x = 0;
      container.y = 0;
      Core.scene.getViewport().setWorldWidth(width);
      Core.scene.getViewport().setWorldHeight(height);

      container.draw();

      FrameBuffer buff = new FrameBuffer();
      int imageWidth = (int) (width*scl);
      int imageHeight = (int) (height*scl);

      buff.resize(imageWidth, imageHeight);
      buff.begin(Color.clear);
      Draw.proj(camera);
      container.draw();
      Draw.flush();
      byte[] lines = ScreenUtils.getFrameBufferPixels(0, 0, imageWidth, imageHeight, true);
      Pixmap fullPixmap = new Pixmap(imageWidth, imageHeight);
      Buffers.copy(lines, 0, fullPixmap.pixels, lines.length);
      buff.end();

      container.parent = par;
      container.x = x;
      container.y = y;
      zoom.scaleX = sclX;
      zoom.scaleY = sclY;
      panX = px;
      panY = py;
      Core.scene.getViewport().setWorldWidth(scW);
      Core.scene.getViewport().setWorldHeight(scH);

      container.draw();

      return new TextureRegion(new Texture(fullPixmap));
    }

    public void read(Reads read){
      cards.each(container::removeChild);
      cards.clear();
      panX = 0;
      panY = 0;
      zoom.scaleX = 1;
      zoom.scaleY = 1;

      LongMap<ItemLinker> linkerMap = new LongMap<>();

      class Pair{
        final long id;
        final float pres;

        Pair(long id, float pres) {
          this.id = id;
          this.pres = pres;
        }
      }

      ObjectMap<ItemLinker, Seq<Pair>> links = new ObjectMap<>();

      int cardsLen = read.i();
      for (int i = 0; i < cardsLen; i++) {
        Card card = Card.read(read);
        addCard(card, false);
        card.build();
        card.mul = read.i();
        card.setBounds(read.f(), read.f(), read.f(), read.f());

        int inputs = read.i();
        int outputs = read.i();

        for (int l = 0; l < inputs; l++) {
          ItemLinker linker = readLinker(read);
          linkerMap.put(linker.id, linker);
          card.addIn(linker);
        }

        for (int l = 0; l < outputs; l++) {
          ItemLinker linker = readLinker(read);
          linkerMap.put(linker.id, linker);
          card.addOut(linker);

          int n = read.i();
          Seq<Pair> linkTo = new Seq<>();
          links.put(linker, linkTo);
          for (int i1 = 0; i1 < n; i1++) {
            linkTo.add(new Pair(read.l(), read.f()));
          }
        }
      }

      for (ObjectMap.Entry<ItemLinker, Seq<Pair>> link : links) {
        for (Pair pair : link.value) {
          ItemLinker target = linkerMap.get(pair.id);
          link.key.linkTo(target);
          link.key.setPresent(target, pair.pres);
        }
      }

      newSet = null;
    }

    private ItemLinker readLinker(Reads read) {
      long id = read.l();
      ItemLinker res = new ItemLinker(TooManyItems.itemsManager.getByName(read.str()), read.bool(), id);
      res.dir = read.i();
      res.amount = read.f();
      res.expectAmount = read.f();
      res.setBounds(read.f(), read.f(), read.f(), read.f());
      return res;
    }

    public void write(Writes write){
      write.i(cards.size);
      for (Card card : cards) {
        card.write(write);
        write.i(card.mul);
        write.f(card.x);
        write.f(card.y);
        write.f(card.getWidth());
        write.f(card.getHeight());

        write.i(card.in.size);
        write.i(card.out.size);

        for (ItemLinker linker : card.in) {
          writeLinker(write, linker);
        }

        for (ItemLinker linker : card.out) {
          writeLinker(write, linker);

          write.i(linker.links.size);

          for (ObjectMap.Entry<ItemLinker, float[]> entry : linker.links) {
            write.l(entry.key.id);
            write.f(entry.value[0]);
          }
        }
      }
    }

    private void writeLinker(Writes write, ItemLinker linker) {
      write.l(linker.id);
      write.str(linker.item.name());
      write.bool(linker.isInput);
      write.i(linker.dir);
      write.f(linker.amount);
      write.f(linker.expectAmount);

      write.f(linker.x);
      write.f(linker.y);
      write.f(linker.getWidth());
      write.f(linker.getHeight());
    }
  }

  private static boolean useKeyboard() {
    return !mobile || Core.settings.getBool("keyboard");
  }

  protected abstract class Card extends Table {
    protected static final IntMap<Func<Reads, Card>> provs = new IntMap<>();

    final Table child;

    final Seq<ItemLinker> out = new Seq<>();
    final Seq<ItemLinker> in = new Seq<>();

    boolean removing;

    public int mul = 1;

    public static Card read(Reads read) {
      int id = read.i();
      return provs.get(id).get(read);
    }

    public Card() {
      this.child = new Table(Consts.darkGrayUIAlpha){
        @Override
        protected void drawBackground(float x, float y) {
          if (view.newSet == Card.this) {
            Lines.stroke(Scl.scl(5));
            Draw.color(Pal.accentBack, parentAlpha);
            Lines.rect(x - Scl.scl(45), y - Scl.scl(45), getWidth() + 2*Scl.scl(40), getHeight() + 2*Scl.scl(40));
            Draw.color(Pal.accent, parentAlpha);
            Lines.rect(x - Scl.scl(40), y - Scl.scl(40), getWidth() + 2*Scl.scl(40), getHeight() + 2*Scl.scl(40));
            Draw.color();
          }
          super.drawBackground(x, y);
        }
      }.margin(12);

      add(child).fill().pad(100);
    }

    public void rise(){
      view.cards.remove(this);
      view.container.removeChild(this);

      view.cards.add(this);
      view.container.addChild(this);
    }

    public void addIn(ItemLinker linker){
      in.add(linker);
      addChild(linker);
    }

    public void addOut(ItemLinker linker){
      out.add(linker);
      addChild(linker);
    }

    public ItemLinker hitLinker(float x, float y){
      for (ItemLinker linker : seq.clear().addAll(in).addAll(out)) {
        if (x > linker.x - linker.getWidth()/2
            && x < linker.x + linker.getWidth()*1.5f
            && y > linker.y - linker.getHeight()/2
            && y < linker.y + linker.getHeight()*1.5f) {
          return linker;
        }
      }
      return null;
    }

    @Override
    public boolean removeChild(Element element, boolean unfocus) {
      if (element instanceof ItemLinker l){
        in.remove(l);
        out.remove(l);
      }
      return super.removeChild(element, unfocus);
    }

    @Override
    public void layout() {
      super.layout();
      pack();
      child.setPosition(getWidth()/2, getHeight()/2, Align.center);
    }

    @Override
    public void draw() {
      Draw.mixcol(removing? Color.crimson: Pal.accent, removing || selects.contains(this)? 0.5f: 0);
      super.draw();
      Draw.mixcol();
    }

    public abstract void build();
    public abstract void buildLinker();
    public abstract Iterable<RecipeItemStack> accepts();
    public abstract Iterable<RecipeItemStack> outputs();

    protected EventListener moveListener(Element element){
      return new ElementGestureListener(){
        boolean enabled;

        @Override
        public void touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
          if (pointer != 0 || button != KeyCode.mouseLeft || view.isSelecting) return;
          enabled = true;
          moveLock(true);

          Card.this.removing = false;
          selects.each(e -> e.removing = false);
          rise();
        }

        @Override
        public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
          if (pointer != 0 || button != KeyCode.mouseLeft || view.isSelecting) return;
          enabled = false;
          moveLock(false);

          tmp.set(element.x, element.y);
          element.parent.localToStageCoordinates(tmp);

          if (removeMode && tmp.y < Core.scene.getHeight()*0.15f){
            if (selects.contains(Card.this)) selects.each(view::removeCard);
            else view.removeCard(Card.this);
          }
        }

        @Override
        public void pan(InputEvent event, float x, float y, float deltaX, float deltaY) {
          if (!enabled) return;
          if (selects.contains(Card.this)){
            selects.each(e -> e.moveBy(deltaX, deltaY));
          }
          else moveBy(deltaX, deltaY);

          selectLock(true);

          tmp.set(element.x, element.y);
          element.parent.localToStageCoordinates(tmp);

          boolean b = removeMode && tmp.y < Core.scene.getHeight()*0.15f;
          if (selects.contains(Card.this)) selects.each(e -> e.removing = b);
          else Card.this.removing = b;
        }
      };
    }

    public abstract Card copy() ;

    public abstract void write(Writes write);
  }

  protected class RecipeCard extends Card {
    private static final int CLASS_ID = 2134534563;
    static {
      provs.put(CLASS_ID, r -> {
        int id = r.i();
        return TooManyItems.schematicDesigner.new RecipeCard(TooManyItems.recipesManager.getByID(id));
      });
    }

    final Recipe recipe;
    final RecipeView recipeView;

    final EnvParameter parameter = new EnvParameter();

    public RecipeCard(Recipe recipe) {
      this.recipe = recipe;
      this.recipeView = new RecipeView(recipe, (i, m) -> {
        TooManyItems.recipesDialog.toggle = r -> {
          TooManyItems.recipesDialog.hide();
          addRecipe(r);
        };
        TooManyItems.recipesDialog.show();
        TooManyItems.recipesDialog.setCurrSelecting(i, m);
      });
      this.recipeView.validate();
    }

    @Override
    public void build() {
      this.child.table(Consts.grayUI, t -> {
        t.center();
        t.hovered(() -> { if (view.newSet == this) view.newSet = null; });

        t.center().table(Consts.darkGrayUI, top -> {
          top.touchablility = () -> editLock? Touchable.disabled: Touchable.enabled;
          top.add().size(24).pad(4);

          top.hovered(() -> Core.graphics.cursor(Graphics.Cursor.SystemCursor.hand));
          top.exited(() -> Core.graphics.restoreCursor());
          top.addCaptureListener(moveListener(top));
        }).fillY().growX().get();
        t.row();
        t.table(inner -> {
          Table[] tab = new Table[1];
          inner.table(inf -> {
            inf.left().add("").growX().update(l -> l.setText(Core.bundle.format("dialog.calculator.recipeMulti", mul))).left().pad(6).padLeft(12).align(Align.left);
            inf.add(Core.bundle.format("dialog.calculator.config")).padLeft(30);
            inf.button(Icon.pencil, Styles.clearNonei, 32, () -> tab[0].visible = true).margin(4);
          }).growX();
          inner.row();
          inner.table(i -> i.add(recipeView).fill()).center().fill().pad(36).padTop(12);

          inner.fill(over -> {
            tab[0] = over;
            over.visible = false;
            over.table(Consts.darkGrayUIAlpha, table -> {
              table.top();
              table.table(Consts.grayUI, b -> {
                b.table(Consts.darkGrayUIAlpha, pane -> {
                  pane.add(Core.bundle.get("dialog.calculator.config")).growX().padLeft(10);
                  pane.button(Icon.cancel, Styles.clearNonei, 32, () -> tab[0].visible = false).margin(4);
                }).growX();
              }).growX();
              table.row();
              table.table(r -> {
                r.add(Core.bundle.get("calculator.config.multiple"));
                r.table(inp -> {
                  inp.field(Integer.toString(mul), TextField.TextFieldFilter.digitsOnly, i -> {
                    try {
                      mul = i.isEmpty()? 0: Integer.parseInt(i);
                    } catch (Throwable ignored){}
                  }).growX().get().setAlignment(right);
                  inp.add("x").color(Color.gray);
                }).growX().padLeft(20);
                r.row();


              }).margin(10).fill();
            }).grow();
          });
        });
      }).fill();
    }

    @Override
    public void buildLinker() {
      for (RecipeItemStack item : outputs()) {
        if (((GeneratorRecipe) RecipeType.generator).isPower(item.item)) continue;

        ItemLinker linker = new ItemLinker(item.item, false);
        addOut(linker);
      }

      Core.app.post(() -> {
        float outStep = child.getWidth()/out.size;
        float baseOff = outStep/2;
        for (int i = 0; i < out.size; i++) {
          ItemLinker linker = out.get(i);

          linker.pack();
          float offY = child.getHeight()/2 + linker.getHeight()/1.5f;
          float offX = baseOff + i*outStep;

          linker.setPosition(child.x + offX, child.y + child.getHeight()/2 + offY, Align.center);
          linker.dir = 1;
        }
      });
    }

    @Override
    public Iterable<RecipeItemStack> accepts() {
      return recipe.materials.values();
    }

    @Override
    public Iterable<RecipeItemStack> outputs() {
      return recipe.productions.values();
    }

    @Override
    public RecipeCard copy() {
      RecipeCard res = new RecipeCard(recipe);
      res.mul = mul;

      res.setBounds(x, y, width, height);

      return res;
    }

    @Override
    public void write(Writes write) {
      write.i(CLASS_ID);
      write.i(recipe.hashCode());
    }
  }

  protected class IOCard extends Card{
    private static final int CLASS_ID = 1213124234;
    static {
      provs.put(CLASS_ID, r -> {
        IOCard res = TooManyItems.schematicDesigner.new IOCard(TooManyItems.itemsManager.getByName(r.str()), r.bool());
        res.stack.amount = r.f();
        return res;
      });
    }

    public final RecipeItemStack stack;

    private final Iterable<RecipeItemStack> itr;
    private final boolean isInput;

    public IOCard(RecipeItem<?> item, boolean isInput) {
      this.isInput = isInput;
      stack = new RecipeItemStack(item, 0).setPresecFormat();
      itr = () -> new Iterator<>() {
        boolean has = false;
        @Override public boolean hasNext() {return has = !has;}
        @Override public RecipeItemStack next() {return stack;}
      };
    }

    @Override
    public void build() {
      this.child.table(Consts.grayUI, t -> {
        t.center();
        t.hovered(() -> { if (view.newSet == this) view.newSet = null; });

        t.center().table(Consts.darkGrayUI, top -> {
          top.touchablility = () -> editLock? Touchable.disabled: Touchable.enabled;
          top.add().size(24).pad(4);

          top.hovered(() -> Core.graphics.cursor(Graphics.Cursor.SystemCursor.hand));
          top.exited(() -> Core.graphics.restoreCursor());
          top.addCaptureListener(moveListener(top));
        }).fillY().growX().get();
        t.row();
        t.table(m -> {
          m.image(isInput? Icon.download: Icon.upload).scaling(Scaling.fit).size(26).padRight(6);
          m.add(Core.bundle.get(isInput? "dialog.calculator.input": "dialog.calculator.output")).growX().labelAlign(left).pad(12);
        });
        t.row();
        t.table(inner -> {
          inner.image(stack.item.icon()).scaling(Scaling.fit).size(48);
          inner.row();
          inner.add(stack.item.localizedName()).pad(8).color(Color.lightGray);
          inner.row();
          inner.table(ta -> {
            Runnable[] edit = new Runnable[1];
            Runnable[] build = new Runnable[1];

            build[0] = () -> {
              ta.clearChildren();
              ta.add(Core.bundle.format("misc.amount", stack.amount > 0? stack.getAmount(): Core.bundle.get("misc.unset"))).minWidth(120);
              ta.button(Icon.pencil, Styles.clearNonei, 32, () -> edit[0].run()).margin(4);
            };
            edit[0] = () -> {
              ta.clearChildren();
              ta.field(Float.toString(stack.amount*60), TextField.TextFieldFilter.floatsOnly, s -> {
                try {
                  stack.amount = Float.parseFloat(s)/60;
                } catch (Throwable ignored){}
              }).width(100);
              ta.add(StatUnit.perSecond.localized());
              ta.button(Icon.ok, Styles.clearNonei, 32, () -> build[0].run()).margin(4);
            };

            build[0].run();
          });
        }).center().fill().pad(36).padTop(24);
      }).fill();
    }

    @Override
    public void buildLinker() {
      if (!isInput) return;

      addOut(new ItemLinker(stack.item, false));

      Core.app.post(() -> {
        ItemLinker linker = out.get(0);

        linker.pack();
        float offY = child.getHeight()/2 + linker.getHeight()/1.5f;
        float offX = child.getWidth()/2;

        linker.setPosition(child.x + offX, child.y + child.getHeight()/2 + offY, Align.center);
        linker.dir = 1;
      });
    }

    @Override
    public Iterable<RecipeItemStack> accepts() {
      return itr;
    }

    @Override
    public Iterable<RecipeItemStack> outputs() {
      return itr;
    }

    public IOCard copy() {
      IOCard res = new IOCard(stack.item, isInput);
      res.stack.amount = stack.amount;

      res.setBounds(x, y, width, height);

      return res;
    }

    @Override
    public void write(Writes write) {
      write.i(CLASS_ID);
      write.str(stack.item.name());
      write.bool(isInput);
      write.f(stack.amount);
    }
  }

  protected class ItemLinker extends Table {
    final long id;
    public final RecipeItem<?> item;
    public float amount = 0;
    public float expectAmount = 0;

    public final boolean isInput;

    OrderedMap<ItemLinker, float[]> links = new OrderedMap<>();
    int dir;

    Vec2 linkPos = new Vec2();
    boolean linking, moving, tim;
    float time;
    Vec2 hovering = new Vec2();
    @Nullable ItemLinker hover, temp;
    @Nullable Card hoverCard;
    boolean hoverValid;

    ItemLinker(RecipeItem<?> item, boolean input){
      this(item, input, new Rand(System.nanoTime()).nextLong());
    }

    ItemLinker(RecipeItem<?> item, boolean input, long id) {
      this.id = id;
      this.item = item;
      this.isInput = input;

      touchablility = () -> editLock? Touchable.disabled: Touchable.enabled;

      stack(
          new Table(t -> {
            t.image(item.icon()).center().scaling(Scaling.fit).size(48);
          }),
          new Table(inc -> {
            inc.bottom();
            inc.add("", Styles.outlineLabel).update(l -> {
              float correction = isInput? expectAmount - amount: amount;
              l.setText(expectAmount <= 0? "": ((expectAmount*60 > 1000? UI.formatAmount((long) (expectAmount*60)): Strings.autoFixed(expectAmount*60, 1)) + "/s\n"
              + (correction < 0? "-": "+") + (correction*60 > 1000? UI.formatAmount((long) (correction*60)): Strings.autoFixed(correction*60, 1)) + "/s"));
            }).center().growX().get().setAlignment(Align.center);
            inc.row();
          })
      ).size(60);

      hovered(() -> Core.graphics.cursor(Graphics.Cursor.SystemCursor.hand));
      exited(() -> Core.graphics.restoreCursor());

      addCaptureListener(new ElementGestureListener() {
        float beginX, beginY;
        boolean panned;

        @Override
        public void touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
          if (pointer != 0) return;

          ((Card) parent).rise();
          moveLock(true);

          setOrigin(Align.center);
          setTransform(true);

          beginX = x;
          beginY = y;

          tim = true;
          time = Time.globalTime;
          linking = false;
          moving = false;

          panned = false;

          if (isInput) {
            tim = false;
            moving = true;
            hover = ItemLinker.this;
            hovering.set(Core.input.mouse());
            clearActions();
            actions(Actions.scaleTo(1.1f, 1.1f, 0.3f));
            tim = false;
          }
        }

        @Override
        public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
          if (pointer != 0) return;

          if (!isInput && linking) {
            if (hover != null && hoverValid) {
              if (linkTo(hover)) {
                if (hover.parent == null) hoverCard.addIn(hover);
              } else {
                deLink(hover);
                if (hover.links.isEmpty()) hoverCard.removeChild(hover);
              }
            }
          }

          if (moving){
            clearActions();
            actions(Actions.scaleTo(1f, 1f, 0.3f));
          }

          moveLock(false);
          resetHov();
          temp = null;
          tim = false;
          linking = false;
          moving = false;
        }

        @Override
        public void pan(InputEvent event, float x, float y, float deltaX, float deltaY) {
          super.pan(event, x, y, deltaX, deltaY);

          if (!panned && Math.abs(x - beginX) < 14 && Math.abs(y - beginY) < 14) return;

          if (!panned){
            panned = true;
          }

          if (tim && !isInput && Time.globalTime - time < 30){
            linking = true;
            hovering.set(Core.input.mouse());
            tim = false;
          }

          hovering.set(Core.input.mouse());
          if (linking) {
            checkLinking();
          }
          else if (moving){
            checkMoving();
          }
        }
      });

      update(() -> {
        if (tim && Time.globalTime - time > 30){
          moving = true;
          hover = ItemLinker.this;
          hovering.set(Core.input.mouse());
          clearActions();
          actions(Actions.scaleTo(1.1f, 1.1f, 0.3f));
          tim = false;
        }
      });
    }

    void checkMoving() {
      Card card = (Card) parent;

      card.stageToLocalCoordinates(hovering);
      adsorption(hovering.x, hovering.y, card);
    }

    void checkLinking(){
      hover = null;
      Card card = view.hitCard(hovering.x, hovering.y, false);

      if (card != null && card != parent) {
        card.stageToLocalCoordinates(hovering);

        hoverValid = false;
        for (RecipeItemStack accept : card.accepts()) {
          if (accept.item == item) {
            hoverValid = true;
            break;
          }
        }

        ItemLinker linker = card.hitLinker(hovering.x, hovering.y);
        if (linker != null){
          if (!linker.isInput || linker.item.item != item.item) hoverValid = false;
          else {
            hover = linker;
            hoverCard = card;

            hovering.set(hover.x + hover.width/2, hover.y + hover.height/2);
            card.localToStageCoordinates(hovering);

            return;
          }
        }

        hoverCard = card;

        if (hover == null) {
          if (temp == null || temp.item != item) {
            temp = new ItemLinker(item, true);
            temp.draw();
            temp.pack();
          }

          hover = temp;
          hover.parent = card;
        }

        if (!hover.adsorption(hovering.x, hovering.y, card)){
          resetHov();

          card.localToStageCoordinates(hovering);

          return;
        }

        hovering.set(hover.x + hover.width/2, hover.y + hover.height/2);
        card.localToStageCoordinates(hovering);
        hover.parent = null;

        return;
      }

      resetHov();
    }

    boolean adsorption(float posX, float posY, Card targetCard) {
      if (((posX < targetCard.child.x || posX > targetCard.child.x + targetCard.child.getWidth())
      && (posY < targetCard.child.y || posY > targetCard.child.y + targetCard.child.getHeight()))) return false;

      if (posX >= targetCard.child.x + targetCard.child.getWidth()) {
        dir = 0;
        float offX = targetCard.child.getWidth()/2 + getWidth()/1.5f;
        setPosition(targetCard.child.x + targetCard.child.getWidth()/2 + offX, posY, Align.center);
      } else if (posY >= targetCard.child.y + targetCard.child.getHeight()) {
        dir = 1;
        float offY = targetCard.child.getHeight()/2 + getHeight()/1.5f;
        setPosition(posX, targetCard.child.y + targetCard.child.getHeight()/2 + offY, Align.center);
      } else if (posX <= targetCard.child.x) {
        dir = 2;
        float offX = -targetCard.child.getWidth()/2 - getWidth()/1.5f;
        setPosition(targetCard.child.x + targetCard.child.getWidth()/2 + offX, posY, Align.center);
      } else if (posY <= targetCard.child.y) {
        dir = 3;
        float offY = -targetCard.child.getHeight()/2 - getHeight()/1.5f;
        setPosition(posX, targetCard.child.y + targetCard.child.getHeight()/2 + offY, Align.center);
      } else {
        return false;
      }
      return true;
    }

    private void resetHov() {
      hover = null;
      hoverCard = null;
    }

    public boolean linkTo(ItemLinker target){
      if (target.item.item != item.item) return false;

      if (isInput)
        throw new IllegalStateException("Only output can do link");
      if (!target.isInput)
        throw new IllegalStateException("Cannot link input to input");

      boolean cont = links.containsKey(target) && target.links.containsKey(this);

      if (cont) return false;

      links.put(target, new float[]{-1, 0});
      target.links.put(this, new float[]{-1, 0});

      return true;
    }

    public void setPresent(ItemLinker target, float pres) {
      if (isInput)
        throw new IllegalStateException("Only output can do link");
      if (!target.isInput)
        throw new IllegalStateException("Cannot link input to input");

      links.get(target)[0] = pres;
      target.links.get(this)[0] = pres;
    }

    public void deLink(ItemLinker target){
      if (target.item.item != item.item) return;

      links.remove(target);
      target.links.remove(this);
    }

    public Vec2 getLinkPos(){
      return linkPos;
    }

    private void updateLinkPos() {
      Point2 p = Geometry.d4(dir);
      linkPos.set(p.x, p.y).scl(width/2 + Scl.scl(24), height/2 + Scl.scl(24)).add(width/2, height/2).add(x, y);
    }

    @Override
    public void draw() {
      super.draw();

      updateLinkPos();

      Lines.stroke(Scl.scl(4));
      Draw.alpha(parentAlpha);
      for (ItemLinker link : links.keys()) {
        if (!link.isInput) continue;

        drawLinkLine(link.getLinkPos(), link.dir);
      }

      Color c = linking? hoverCard == null || (hoverValid && !hover.links.containsKey(this))? Pal.accent: Color.crimson: Color.white;
      Draw.color(c, parentAlpha);
      Vec2 pos = getLinkPos();

      int angle = dir*90 + (isInput? 180: 0);
      float triangleRad = Scl.scl(12);
      Fill.tri(
          pos.x + Angles.trnsx(angle, triangleRad), pos.y + Angles.trnsy(angle, triangleRad),
          pos.x + Angles.trnsx(angle + 120, triangleRad), pos.y + Angles.trnsy(angle + 120, triangleRad),
          pos.x + Angles.trnsx(angle - 120, triangleRad), pos.y + Angles.trnsy(angle - 120, triangleRad)
      );

      if (linking) {
        if (hover != null){
          if (hover.parent == null && hoverCard != null){
            Tmp.v1.set(hoverCard.x, hoverCard.y);

            float cx = hover.x;
            float cy = hover.y;

            Tmp.v2.set(hovering);
            stageToLocalCoordinates(Tmp.v2);
            hover.setPosition(x + Tmp.v2.x, y + Tmp.v2.y, Align.center);
            Draw.mixcol(Color.crimson, hoverValid? 0: 0.5f);
            hover.draw();
            Draw.mixcol();
            Lines.stroke(Scl.scl(4), c);
            drawLinkLine(hover.getLinkPos(), hover.dir);
            hover.x = cx;
            hover.y = cy;
          }
          else {
            Lines.stroke(Scl.scl(4), c);
            drawLinkLine(hover.getLinkPos(), hover.dir);
          }
        }
        else {
          Vec2 lin = getLinkPos();
          Tmp.v2.set(hovering);
          stageToLocalCoordinates(Tmp.v2);

          Lines.stroke(Scl.scl(4), c);
          drawLinkLine(
              lin.x, lin.y, dir,
              x + Tmp.v2.x, y + Tmp.v2.y, dir - 2
          );

          int an = dir*90;
          Fill.tri(
              x + Tmp.v2.x + Angles.trnsx(an, triangleRad), y + Tmp.v2.y + Angles.trnsy(an, triangleRad),
              x + Tmp.v2.x + Angles.trnsx(an + 120, triangleRad), y + Tmp.v2.y + Angles.trnsy(an + 120, triangleRad),
              x + Tmp.v2.x + Angles.trnsx(an - 120, triangleRad), y + Tmp.v2.y + Angles.trnsy(an - 120, triangleRad)
          );
        }
      }
    }

    void drawLinkLine(Vec2 to, int tdir) {
      Vec2 from = getLinkPos();
      drawLinkLine(from.x, from.y, dir, to.x, to.y, tdir);
    }

    void drawLinkLine(Vec2 from, int fdir, Vec2 to, int tdir) {
      drawLinkLine(from.x, from.y, fdir, to.x, to.y, tdir);
    }

    void drawLinkLine(float fx, float fy, int fdir, float tx, float ty, int tdir) {
      float dst = Mathf.dst(fx, fy, tx, ty);
      float off = dst*0.35f;

      Point2 p1 = Geometry.d4(fdir);
      Point2 p2 = Geometry.d4(tdir);

      Lines.curve(
          fx, fy,
          fx + p1.x*off, fy + p1.y*off,
          tx + p2.x*off, ty + p2.y*off,
          tx, ty,
          (int) (dst/0.45f)
      );
    }

    public ItemLinker copy(){
      ItemLinker res = new ItemLinker(item, isInput);

      res.setBounds(x, y, width, height);
      res.amount = amount;
      res.expectAmount = amount;
      res.dir = dir;

      return res;
    }
  }
}
