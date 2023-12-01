package tmi.ui;

import arc.Core;
import arc.Graphics;
import arc.func.Cons;
import arc.func.Func;
import arc.func.Prov;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Geometry;
import arc.math.geom.Point2;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.scene.Action;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.actions.Actions;
import arc.scene.event.ElementGestureListener;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.scene.utils.Elem;
import arc.struct.Seq;
import arc.util.*;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.core.UI;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import tmi.TooManyItems;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeType;
import tmi.recipe.types.GeneratorRecipe;
import tmi.recipe.types.RecipeItem;
import tmi.util.Consts;

import static arc.util.Align.*;
import static arc.util.Align.bottom;

public class SchematicCalculatorDialog extends BaseDialog {
  private static final Seq<ItemLinker> seq = new Seq<>();
  private static final Vec2 tmp = new Vec2();
  protected final View view;

  protected final Table menuTable = new Table(){{ visible = false; }};

  protected Table removeArea, sideTools;
  protected boolean editLock, removeMode;

  public SchematicCalculatorDialog() {
    super("");

    titleTable.clear();

    addCloseButton();

    cont.table().grow().get().add(view = new View()).grow();

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
          TooManyItems.recipesDialog.show();
        });
        sideBar.row();

        sideBar.button(Icon.refresh, Styles.clearNonei, 32, view::standardization);
        sideBar.row();

        sideBar.button(Icon.download, Styles.clearNonei, 32, () -> {

        });
        sideBar.row();

        sideBar.button(Icon.save, Styles.clearNonei, 32, () -> {

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
        sideBar.button(Icon.infoCircle, Styles.clearNoneTogglei, 32, () -> {

        }).padBottom(0);
        sideBar.row();
      }).left().growY().fillX().padTop(100).padBottom(100);
    });
  }

  public void addRecipe(Recipe recipe){
    view.addRecipeCard(new RecipeCard(recipe));
  }

  public void moveLock(boolean lock){
    view.lock = lock;
  }

  public void showMenu(Cons<Table> tabBuilder, Element showOn, int alignment, int tableAlign){
    menuTable.clear();
    tabBuilder.get(menuTable);
    menuTable.draw();
    menuTable.act(1);
    menuTable.setSize(menuTable.getPrefWidth(), menuTable.getPrefHeight());

    menuTable.visible = true;

    Vec2 v = new Vec2();
    Runnable r;
    menuTable.update(r = () -> {
      menuTable.setSize(menuTable.getPrefWidth(), menuTable.getPrefHeight());
      v.set(showOn.x, showOn.y);

      if((alignment & right) != 0)
        v.x += showOn.getWidth();
      else if((alignment & left) == 0)
        v.x += showOn.getWidth() / 2;

      if((alignment & top) != 0)
        v.y += showOn.getHeight();
      else if((alignment & bottom) == 0)
        v.y += showOn.getHeight() / 2;

      showOn.parent.localToAscendantCoordinates(view.zoom, v);
      menuTable.setPosition(v.x, v.y, tableAlign);
    });

    r.run();
  }

  public void hideMenu(){
    menuTable.visible = false;
  }

  protected class View extends Group{
    final Seq<RecipeCard> recipeCards = new Seq<>();

    RecipeCard newSet;
    boolean lock;

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

          Lines.stroke(4, Pal.gray);
          for (float offX = 0; offX <= (Core.scene.getWidth()/2)/zoom.scaleX - panX; offX += 150){
            Lines.line(x + offX, -Core.scene.getHeight()/zoom.scaleY, x + offX, Core.scene.getHeight()/zoom.scaleY*2);
          }
          for (float offX = 0; offX >= -(Core.scene.getWidth()/2)/zoom.scaleX - panX; offX -= 150){
            Lines.line(x + offX, -Core.scene.getHeight()/zoom.scaleY, x + offX, Core.scene.getHeight()/zoom.scaleY*2);
          }

          for (float offY = 0; offY <= (Core.scene.getHeight()/2)/zoom.scaleY - panY; offY += 150){
            Lines.line(-Core.scene.getWidth()/zoom.scaleX, y + offY, Core.scene.getWidth()/zoom.scaleX*2, y + offY);
          }
          for (float offY = 0; offY >= -(Core.scene.getHeight()/2)/zoom.scaleY - panY; offY -= 150){
            Lines.line(-Core.scene.getWidth()/zoom.scaleX, y + offY, Core.scene.getWidth()/zoom.scaleX*2, y + offY);
          }
          super.draw();
        }
      };
      zoom.addChild(container);
      zoom.addChild(menuTable);
      fill(t -> t.add(zoom).grow());

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

      addCaptureListener(new ElementGestureListener(){
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
        public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button){
          lastZoom = zoom.scaleX;
        }

        @Override
        public void pan(InputEvent event, float x, float y, float deltaX, float deltaY){
          if (lock) return;

          panX += deltaX/zoom.scaleX;
          panY += deltaY/zoom.scaleY;
          clamp();
        }
      });
    }

    public void addRecipeCard(RecipeCard card){
      recipeCards.add(card);
      container.addChild(card);

      tmp.set(Core.scene.getWidth()/2, Core.scene.getHeight()/2);
      container.stageToLocalCoordinates(tmp);

      card.draw();
      card.setPosition(tmp.x, tmp.y, Align.center);
      newSet = card;
    }

    public void removeRecipeCard(RecipeCard card){
      recipeCards.remove(card);
      container.removeChild(card);

      seq.clear().addAll(card.in).addAll(card.out);
      for (ItemLinker linker : seq) {
        for (ItemLinker link : linker.links) {
          linker.deLink(link);
          if (link.links.isEmpty()) link.remove();
        }
      }
    }

    private void clamp() {
      Group par = parent;
      if (par == null) return;
    }

    RecipeCard hitCard(float stageX, float stageY){
      for (RecipeCard card : recipeCards) {
        tmp.set(card.x, card.y);
        card.parent.localToStageCoordinates(tmp);
        float ox = tmp.x;
        float oy = tmp.y;
        tmp.set(card.x + card.getWidth(), card.y + card.getHeight());
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

      for (RecipeCard card : recipeCards) {
        v1.x = Math.min(v1.x, card.x);
        v1.y = Math.min(v1.y, card.y);

        v2.x = Math.max(v2.x, card.x + card.getWidth());
        v2.y = Math.max(v2.y, card.y + card.getHeight());
      }

      v2.add(v1).scl(0.5f);

      float offX = -v2.x - container.getWidth()/2;
      float offY = -v2.y - container.getHeight()/2;

      for (RecipeCard card : recipeCards) {
        card.moveBy(offX, offY);
      }

      panX = 0;
      panY = 0;
    }

    public void read(Reads read){

    }

    public void write(Writes write){

    }
  }

  protected class RecipeCard extends Table {
    final Recipe recipe;
    final RecipeView recipeView;
    final Long id;

    final Table child;

    final Seq<ItemLinker> out = new Seq<>();
    final Seq<ItemLinker> in = new Seq<>();

    boolean removing;

    public int mul = 1;

    public RecipeCard(Recipe recipe) {
      this(recipe, new Rand(System.nanoTime()).nextLong());
    }

    public RecipeCard(Recipe recipe, long id) {
      this.id = id;
      this.recipe = recipe;
      this.recipeView = new RecipeView(recipe, (i, m) -> {
        TooManyItems.recipesDialog.show();
        TooManyItems.recipesDialog.setCurrSelecting(i, m);
      });
      this.recipeView.validate();

      this.child = new Table(Consts.darkGrayUIAlpha){
        @Override
        protected void drawBackground(float x, float y) {
          if (view.newSet == RecipeCard.this) {
            Lines.stroke(Scl.scl(5));
            Draw.color(Pal.accentBack);
            Lines.rect(x - Scl.scl(45), y - Scl.scl(45), getWidth() + 2*Scl.scl(40), getHeight() + 2*Scl.scl(40));
            Draw.color(Pal.accent);
            Lines.rect(x - Scl.scl(40), y - Scl.scl(40), getWidth() + 2*Scl.scl(40), getHeight() + 2*Scl.scl(40));
            Draw.color();
          }
          super.drawBackground(x, y);
        }
      }.margin(12);
      this.child.table(Consts.grayUI, t -> {
        t.center();
        t.hovered(() -> { if (view.newSet == this) view.newSet = null; });

        Table pad = t.center().table(Consts.darkGrayUI, top -> {
          top.touchablility = () -> editLock? Touchable.disabled: Touchable.enabled;
          top.add().size(24).pad(4);

          top.hovered(() -> Core.graphics.cursor(Graphics.Cursor.SystemCursor.hand));
          top.exited(() -> Core.graphics.restoreCursor());
          top.addCaptureListener(new ElementGestureListener(){
            @Override
            public void touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
              super.touchDown(event, x, y, pointer, button);
              moveLock(true);

              RecipeCard.this.removing = false;
              rise();
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
              super.touchUp(event, x, y, pointer, button);
              moveLock(false);

              tmp.set(top.x, top.y);
              top.parent.localToStageCoordinates(tmp);

              if (removeMode && tmp.y < Core.scene.getHeight()*0.15f){
                view.removeRecipeCard(RecipeCard.this);
              }
            }

            @Override
            public void pan(InputEvent event, float x, float y, float deltaX, float deltaY) {
              super.pan(event, x, y, deltaX, deltaY);

              moveBy(deltaX, deltaY);

              tmp.set(top.x, top.y);
              top.parent.localToStageCoordinates(tmp);

              RecipeCard.this.removing = removeMode && tmp.y < Core.scene.getHeight()*0.15f;
            }
          });
        }).fillY().growX().get();
        t.row();
        t.table(inf -> {
          inf.left().add("").growX().update(l -> l.setText(Core.bundle.format("dialog.calculator.recipeMulti", mul))).left().pad(6).padLeft(12).align(Align.left);
          inf.add(Core.bundle.format("dialog.calculator.config")).padLeft(30);
          inf.button(Icon.pencil, Styles.clearNonei, 32, () -> showMenu(menu -> {
            menu.table(Consts.darkGrayUIAlpha, table -> {
              table.top();
              table.table(Consts.grayUI, b -> {
                b.table(Consts.darkGrayUIAlpha, pane -> {
                  pane.add(Core.bundle.get("dialog.calculator.config")).growX().padLeft(10);
                  pane.button(Icon.cancel, Styles.clearNonei, 32, SchematicCalculatorDialog.this::hideMenu).margin(4);
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
            }).fill().minSize(t.getWidth(), t.getHeight() - pad.getHeight());
          }, inf, topLeft, topLeft)).margin(4);
        }).growX();
        t.row();
        t.pane(i -> i.add(recipeView).fill()).center().fill().padTop(8).pad(36).padTop(12);
      }).fill();

      add(child).fill().pad(100);

      for (RecipeItem<?> item : recipe.productions.keys()) {
        if (((GeneratorRecipe) RecipeType.generator).isPower(item)) continue;

        ItemLinker linker = new ItemLinker(item, false);
        addOut(linker);
      }

      Core.app.post(() -> {
        float outStep = child.getWidth()/out.size;
        float baseOff = outStep/2;
        for (int i = 0; i < out.size; i++) {
          ItemLinker linker = out.get(i);

          linker.setSize(linker.getPrefWidth(), linker.getPrefHeight());
          float offY = child.getHeight()/2 + linker.getHeight()/1.5f;
          float offX = baseOff + i*outStep;

          linker.setPosition(child.x + offX, child.y + child.getHeight()/2 + offY, Align.center);
          linker.dir = 1;
        }
      });
    }

    public void rise(){
      view.recipeCards.remove(this);
      view.container.removeChild(this);

      view.recipeCards.add(this);
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
      setSize(getPrefWidth(), getPrefHeight());
      child.setPosition(getWidth()/2, getHeight()/2, Align.center);
    }

    @Override
    public void draw() {
      Draw.mixcol(Color.crimson, removing? 0.5f: 0);
      super.draw();
      Draw.mixcol();
    }

    public void read(Reads read){}

    public void write(Writes write){}
  }

  protected class ItemLinker extends Table {
    Vec2 linkPos = new Vec2();

    final long id;
    boolean linking, moving;
    Vec2 hovering = new Vec2();
    @Nullable ItemLinker hover, temp;
    @Nullable RecipeCard hoverCard;
    boolean hoverValid;

    final RecipeItem<?> item;
    float amount = -1;
    final boolean isInput;

    Seq<ItemLinker> links = new Seq<>();
    int dir;

    boolean tim;
    float time;

    ItemLinker(RecipeItem<?> item, boolean input){
      this(item, input, new Rand(System.nanoTime()).nextLong());
    }

    ItemLinker(RecipeItem<?> item, boolean input, long id) {
      this.id = id;
      this.item = item;
      this.isInput = input;

      touchablility = () -> editLock? Touchable.disabled: Touchable.enabled;

      table(inc -> {
        inc.image(item.icon()).scaling(Scaling.fit).size(48);
        inc.row();
        inc.add("", Styles.outlineLabel).update(l -> {
          l.setText(amount <= 0? "": (amount*60 > 1000? UI.formatAmount((long) (amount*60)): Strings.autoFixed(amount*60, 1)) + "/s");
        }).center().grow().get().setAlignment(Align.center);
      }).size(60);

      hovered(() -> Core.graphics.cursor(Graphics.Cursor.SystemCursor.hand));
      exited(() -> Core.graphics.restoreCursor());

      addCaptureListener(new ElementGestureListener() {
        float beginX, beginY;
        boolean panned;

        @Override
        public void touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
          if (pointer != 0) return;

          ((RecipeCard) parent).rise();
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
      RecipeCard card = (RecipeCard) parent;

      card.stageToLocalCoordinates(hovering);
      adsorption(hovering.x, hovering.y, card);
    }

    void checkLinking(){
      hover = null;
      RecipeCard card = view.hitCard(hovering.x, hovering.y);

      if (card != null && card != parent) {
        card.stageToLocalCoordinates(hovering);

        hoverValid = card.recipe.materials.containsKey(item);

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
            temp.setSize(temp.getPrefWidth(), temp.getPrefHeight());
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

    boolean adsorption(float posX, float posY, RecipeCard targetCard) {
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

      return links.addUnique(target) && target.links.addUnique(this);
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

      Lines.stroke(4);
      for (ItemLinker link : links) {
        if (!link.isInput) continue;

        drawLinkLine(link.getLinkPos(), link.dir);
      }

      Color c = linking? hoverCard == null || (hoverValid && !hover.links.contains(this))? Pal.accent: Color.crimson: Color.white;
      Draw.color(c);
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
            Lines.stroke(4, c);
            drawLinkLine(hover.getLinkPos(), hover.dir);
            hover.x = cx;
            hover.y = cy;
          }
          else {
            Lines.stroke(4, c);
            drawLinkLine(hover.getLinkPos(), hover.dir);
          }
        }
        else {
          Vec2 lin = getLinkPos();
          Tmp.v2.set(hovering);
          stageToLocalCoordinates(Tmp.v2);

          Lines.stroke(4, c);
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

    public void read(Reads read){}

    public void write(Writes write){}
  }
}
