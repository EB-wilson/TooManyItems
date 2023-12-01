package tmi.ui;

import arc.Core;
import arc.Graphics;
import arc.func.Boolc;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.GlyphLayout;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.event.ElementGestureListener;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.style.Drawable;
import arc.scene.ui.Button;
import arc.scene.ui.Image;
import arc.scene.ui.ImageButton;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.*;
import mindustry.Vars;
import mindustry.ctype.Content;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.ui.Fonts;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import tmi.TooManyItems;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeType;
import tmi.recipe.types.FactoryRecipe;
import tmi.recipe.types.GeneratorRecipe;
import tmi.recipe.types.RecipeItem;
import tmi.util.Consts;

import java.text.Collator;
import java.util.Comparator;

import static mindustry.Vars.mobile;
import static tmi.TooManyItems.binds;
import static tmi.util.Consts.*;

public class RecipesDialog extends BaseDialog {
  private final static Collator compare = Collator.getInstance(Core.bundle.getLocale());

  public Seq<Sorting> sortings = Seq.with(new Sorting(){{
    localized = Core.bundle.get("misc.defaultSort");
    icon = Icon.menu;
    sort = RecipeItem::compareTo;
  }}, new Sorting(){{
    localized = Core.bundle.get("misc.nameSort");
    icon = a_z;
    sort = (a, b) -> compare.compare(a.localizedName(), b.localizedName());
  }}, new Sorting(){{
    localized = Core.bundle.get("misc.modSort");
    icon = Icon.book;
    sort = (a, b) -> {
      if (a.item instanceof Content ca && b.item instanceof Content cb) {
        return ca.minfo.mod == null ? cb.minfo.mod == null ? 0 : -1 : cb.minfo.mod != null ? compare.compare(ca.minfo.mod.name, cb.minfo.mod.name) : 1;
      }
      else return 0;
    };
  }}, new Sorting(){{
    localized = Core.bundle.get("misc.typeSort");
    icon = Icon.file;
    sort = (a, b) -> {
      int n = Integer.compare(a.typeID(), b.typeID());

      if (n == 0){
        if (a.item instanceof Block ba && b.item instanceof Block bb){
          if (ba.hasBuilding() && bb.hasBuilding()){
            if (ba.update && bb.update) return 0;
            else if (ba.update) return 1;
            else if (bb.update) return -1;
          }
          else if (ba.hasBuilding()) return 1;
          else if (bb.hasBuilding()) return -1;
        }
      }

      return n;
    };
  }});

  Table recipesTable, contentsTable, sortingTab, modeTab;
  RecipeItem<?> currentSelect;

  @Nullable Mode recipeMode = null;

  String contentSearch = "";
  Sorting sorting = sortings.first();
  boolean reverse;
  int total, fold, recipeIndex, itemPages, pageItems, currPage;

  Table currZoom;
  RecipeView currView;

  float lastZoom = -1;

  final Seq<RecipeItem<?>> ucSeq = new Seq<>();

  Runnable contentsRebuild, refreshSeq, rebuildRecipe;

  public RecipesDialog() {
    super(Core.bundle.get("dialog.recipes.title"));

    addCloseButton();

    shown(this::buildBase);
    resized(this::buildBase);

    hidden(() -> {
      currentSelect = null;
      recipeMode = null;
      currPage = 0;
      lastZoom = -1;
      sorting = sortings.first();
      cont.clear();
    });
  }

  protected void buildBase() {
    cont.clearChildren();

    if (Core.graphics.isPortrait()){
      recipesTable = cont.table(padGrayUIAlpha).grow().pad(5).get();
      cont.row();

      Table tab = new Table(Consts.grayUIAlpha, t -> contentsTable = t.table(padGrayUIAlpha).growX().height(Core.graphics.getHeight()/2f/Scl.scl()).get()){
        @Override
        public void validate() {
          parent.invalidateHierarchy();
          if (getWidth() != parent.getWidth() || getHeight() != getPrefHeight()){
            setSize(parent.getWidth(), getPrefHeight());
            invalidate();
          }
          super.validate();
        }
      };
      tab.visible = false;
      cont.addChild(tab);

      cont.button(Icon.up, Styles.clearNonei, 32, () -> {
        tab.visible = !tab.visible;
      }).growX().height(40).update(i -> {
        i.getStyle().imageUp = tab.visible ? Icon.downOpen : Icon.upOpen;
        tab.setSize(tab.parent.getWidth(), tab.getPrefHeight());
        tab.setPosition(i.x, i.y + i.getPrefHeight() + 4, Align.bottomLeft);
      });
    }
    else {
      recipesTable = cont.table(padGrayUIAlpha).grow().pad(5).get();
      cont.image().color(Pal.accent).growY().pad(0).width(4);
      contentsTable = cont.table(padGrayUIAlpha).growY().width(Core.graphics.getWidth()/2.5f/Scl.scl()).pad(5).get();
    }

    buildContents();
    buildRecipes();
  }

  @SuppressWarnings("StringRepeatCanBeUsed")
  protected void buildContents() {
    contentsTable.addListener(new InputListener(){
      @Override
      public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY){
        if (amountY < 0 && currPage > 0){
          currPage--;
          contentsRebuild.run();
        }
        else if (amountY > 0 && currPage < itemPages - 1){
          currPage++;
          contentsRebuild.run();
        }
        return true;
      }

      @Override
      public void enter(InputEvent event, float x, float y, int pointer, Element fromActor) {
        contentsTable.requestScroll();
        super.enter(event, x, y, pointer, fromActor);
      }
    });

    contentsTable.table(filter -> {
      filter.add(Core.bundle.get("misc.search"));
      filter.image(Icon.zoom).size(36).scaling(Scaling.fit);
      filter.field(contentSearch, str -> {
        contentSearch = str.toLowerCase();
        refreshSeq.run();
      }).growX();

      sortingTab = new Table(grayUIAlpha, ta -> {
        for (Sorting sort : sortings) {
          ta.button(t -> {
            t.defaults().left().pad(5);
            t.image(sort.icon).size(24).scaling(Scaling.fit);
            t.add(sort.localized).growX();
          }, Styles.clearNoneTogglei, () -> {
            sorting = sort;
            refreshSeq.run();
          }).margin(6).growX().fillY().update(e -> e.setChecked(sorting.equals(sort)));
          ta.row();
        }
      });
      sortingTab.visible = false;

      ImageButton b = filter.button(Icon.up, Styles.clearNonei, 32, () -> {
        sortingTab.visible = !sortingTab.visible;
      }).size(36).get();

      b.update(() -> {
        b.getStyle().imageUp = sorting.icon;
        sortingTab.setSize(sortingTab.getPrefWidth(), sortingTab.getPrefHeight());
        sortingTab.setPosition(b.x, filter.y, Align.top);
      });

      filter.button(bu -> bu.image().size(32).scaling(Scaling.fit).update(i -> i.setDrawable(reverse? Icon.up: Icon.down)), Styles.clearNonei, () -> {
        reverse = !reverse;
        refreshSeq.run();
      }).size(36);
      filter.add("").update(l -> l.setText(Core.bundle.get(reverse? "misc.reverse": "misc.order"))).color(Pal.accent);
    }).padBottom(12).growX();
    contentsTable.row();
    contentsTable.table(t -> {
      refreshSeq = () -> {
        fold = 0;
        total = 0;
        ucSeq.clear();

        for (RecipeItem<?> item : TooManyItems.itemsManager.getList()) {
          if (TooManyItems.recipesManager.anyRecipe(item)){
            total++;
            if (!item.localizedName().toLowerCase().contains(contentSearch) && !item.name().toLowerCase().contains(contentSearch)){
              fold++;
              return;
            }
            ucSeq.add(item);
          }
        }

        if (reverse) ucSeq.sort((a, b) -> sorting.sort.compare(b, a));
        else ucSeq.sort(sorting.sort);

        contentsRebuild.run();
      };

      contentsRebuild = () -> {
        t.validate();
        float width = t.getWidth(), height = t.getHeight();

        t.clearChildren();
        t.left().top().defaults().size(60, 90);

        int xn = (int) (width/Scl.scl(60));
        int yn = (int) (height/Scl.scl(90));

        pageItems = xn*yn;
        itemPages = Mathf.ceil((float) ucSeq.size/pageItems);

        int curX = 0;

        if (currPage < 0) {
          int index = ucSeq.indexOf(currentSelect);
          currPage = index / pageItems;
        }

        currPage = Mathf.clamp(currPage, 0, itemPages - 1);
        int from = currPage*pageItems;
        int to = currPage*pageItems + pageItems;

        for (int i = from; i < to; i++) {
          if (i >= ucSeq.size) break;

          RecipeItem<?> content = ucSeq.get(i);
          buildItem(t, content);

          curX++;
          if (curX >= xn){
            t.row();
            curX = 0;
          }
        }
      };
    }).grow().pad(0);

    contentsTable.row();
    contentsTable.table(butt -> {
      butt.button(Icon.leftOpen, Styles.clearNonei, 32, () -> {
        currPage--;
        contentsRebuild.run();
      }).disabled(b -> currPage <= 0).size(45);
      butt.button("<<", Styles.cleart, () -> {
        currPage = 0;
        contentsRebuild.run();
      }).disabled(b -> currPage <= 0).size(45).get().getStyle().disabled = Styles.none;
      butt.table(t -> {
        t.touchable = Touchable.enabled;
        Boolc[] buildPage = new Boolc[1];
        buildPage[0] = b -> {
          t.clear();

          t.hovered(() -> Core.graphics.cursor(Graphics.Cursor.SystemCursor.hand));
          t.exited(() -> Core.graphics.restoreCursor());

          if (b){
            GlyphLayout l = GlyphLayout.obtain();
            int i = Mathf.ceil(Mathf.log(itemPages, 10));
            StringBuilder s = new StringBuilder();
            for (int n = 0; n < i; n++) {
              s.append("0");
            }
            l.setText(Fonts.def,  s.toString());

            t.add(Core.bundle.get("dialog.recipes.jump_a"));
            t.field(
                Integer.toString(currPage + 1),
                (field, c) -> Character.isDigit(c) && Integer.parseInt(field.getText() + c) > 0 && Integer.parseInt(field.getText() + c) <= itemPages,
                st -> {
                  currPage = st.isEmpty()? 0: Integer.parseInt(st) - 1;
                  contentsRebuild.run();
                }
            ).width(l.width + 45);
            t.add(Core.bundle.format("dialog.recipes.jump_b", itemPages));
            t.update(() -> {
              if (Core.input.justTouched() && Core.scene.hit(Core.input.mouseX(), Core.input.mouseY(), true).parent != t) buildPage[0].get(false);
            });

            l.free();
          }
          else {
            t.add("").update(l -> {
              l.setAlignment(Align.center);
              l.setText(Core.bundle.format("dialog.recipes.pages", currPage + 1, itemPages));
            }).growX();
            t.clicked(() -> buildPage[0].get(true));
          }
        };

        buildPage[0].get(false);
      }).growX();
      butt.button(">>", Styles.cleart, () -> {
        currPage = itemPages - 1;
        contentsRebuild.run();
      }).disabled(b -> currPage >= itemPages - 1).size(45).get().getStyle().disabled = Styles.none;
      butt.button(Icon.rightOpen, Styles.clearNonei, 32, () -> {
        currPage++;
        contentsRebuild.run();
      }).disabled(b -> currPage >= itemPages - 1).size(45);
    }).fillY().growX();
    contentsTable.row();
    contentsTable.add("").color(Color.gray).left().growX().update(l -> l.setText(Core.bundle.format("dialog.recipes.total", total, fold)));

    contentsTable.addChild(sortingTab);

    Core.app.post(() -> refreshSeq.run());
  }

  protected boolean buildRecipes() {
    Seq<Recipe> recipes;

    if (currentSelect != null && !(currentSelect.item instanceof Block) && recipeMode == Mode.factory) recipeMode = null;

    if (currentSelect == null){
      recipes = null;

      recipesTable.clearChildren();
      recipesTable.table(top -> {
        top.table(t -> {
          t.table(Tex.buttonTrans).size(90);
          t.row();
          t.add(Core.bundle.get("dialog.recipes.currSelected")).growX().color(Color.lightGray).get().setAlignment(Align.center);
        });
        top.table(infos -> {
          infos.left().top().defaults().left();
          infos.add(Core.bundle.get("dialog.recipes.unselected")).color(Pal.accent);
        }).grow().padLeft(12).padTop(8);
      }).left().growX().fillY().pad(8);
      recipesTable.row();
      recipesTable.add().grow();
    }
    else {
      if (recipeMode == null) {
        recipeMode = TooManyItems.recipesManager.anyMaterial(currentSelect) ? Mode.usage :
            TooManyItems.recipesManager.anyProduction(currentSelect) ? Mode.recipe :
            currentSelect.item instanceof Block ? TooManyItems.recipesManager.getRecipesByFactory(currentSelect).any()? Mode.factory: Mode.recipe : null;
      }

      recipes = recipeMode == null? null: switch (recipeMode) {
        case usage -> TooManyItems.recipesManager.getRecipesByMaterial(currentSelect);
        case recipe -> TooManyItems.recipesManager.getRecipesByProduction(currentSelect);
        case factory -> TooManyItems.recipesManager.getRecipesByFactory(currentSelect);
      };
    }

    Seq<RecipeView> recipeViews = new Seq<>();
    if (recipes != null) {
      for (Recipe recipe : recipes) {
        RecipeView view = new RecipeView(recipe, this::setCurrSelecting);
        recipeViews.add(view);
      }
    }

    if (recipes == null || recipeViews.isEmpty()) return false;

    recipesTable.clearListeners();
    recipesTable.addListener(new InputListener(){
      @Override
      public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY){
        if (currZoom == null) return false;
        currZoom.setScale(lastZoom = Mathf.clamp(currZoom.scaleX - amountY / 10f * currZoom.scaleX, 0.25f, 1));
        currZoom.setOrigin(Align.center);
        currZoom.setTransform(true);

        clamp(currZoom);
        return true;
      }

      @Override
      public void enter(InputEvent event, float x, float y, int pointer, Element fromActor) {
        recipesTable.requestScroll();
        super.enter(event, x, y, pointer, fromActor);
      }
    });

    recipesTable.addCaptureListener(new ElementGestureListener(){
      @Override
      public void zoom(InputEvent event, float initialDistance, float distance){
        if(lastZoom < 0){
          lastZoom = currZoom.scaleX;
        }

        currZoom.setScale(Mathf.clamp(distance / initialDistance * lastZoom, 0.25f, 1));
        currZoom.setOrigin(Align.center);
        currZoom.setTransform(true);

        clamp(currZoom);
      }

      @Override
      public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button){
        lastZoom = currZoom.scaleX;
      }

      @Override
      public void pan(InputEvent event, float x, float y, float deltaX, float deltaY){
        currZoom.moveBy(deltaX, deltaY);
        clamp(currZoom);
      }
    });

    recipesTable.touchable = Touchable.enabled;

    currZoom = new Table(main -> {
      recipeIndex = 0;
      rebuildRecipe = () -> {
        main.center();
        main.clearChildren();
        currView = recipeViews.get(recipeIndex);
        currView.validate();
        main.table(modes -> {
          modeTab = new Table(grayUIAlpha, ta -> {
            for (Mode mode : Mode.values()) {
              if (mode == Mode.factory && (!(currentSelect.item instanceof Block) || TooManyItems.recipesManager.getRecipesByFactory(currentSelect).isEmpty())) continue;
              else if (mode == Mode.recipe && TooManyItems.recipesManager.getRecipesByProduction(currentSelect).isEmpty()) continue;
              else if (mode == Mode.usage && TooManyItems.recipesManager.getRecipesByMaterial(currentSelect).isEmpty()) continue;

              ta.button(t -> {
                    t.defaults().left().pad(5);
                    t.image(mode.icon()).size(24).scaling(Scaling.fit);
                    t.add(mode.localized()).growX();
                  }, Styles.clearNoneTogglei, () -> setRecipeMode(mode))
                  .margin(6).growX().fillY().update(e -> e.setChecked(mode.equals(recipeMode)));
              ta.row();
            }
          });
          modeTab.visible = false;
          modes.add(new Button(Styles.clearNonei) {{
            touchable = modeTab.getChildren().size > 1 ? Touchable.enabled : Touchable.disabled;

            image().scaling(Scaling.fit).size(32).update(i -> i.setDrawable(recipeMode.icon()));
            add("").padLeft(4).update(l -> l.setText(recipeMode.localized()));

            clicked(() -> modeTab.visible = !modeTab.visible);

            update(() -> {
              modeTab.setSize(modeTab.getPrefWidth(), modeTab.getPrefHeight());
              modeTab.setPosition(modes.x + x + width / 2, modes.y, Align.top);
            });
          }}).margin(8).fill().get();

        }).fill();
        main.row();
        main.table().center().fill().get().add(currView).center().fill();
        main.row();
        main.table(t -> {
          t.center().defaults().center();
          if (currView.recipe.subInfoBuilder != null) {
            currView.recipe.subInfoBuilder.get(t);
          }
        }).fill().padTop(8);

        main.addChild(modeTab);

        main.validate();

        main.setSize(main.getPrefWidth(), main.getPrefHeight());

        float scl = Mathf.clamp((main.parent.getWidth()*0.8f) / main.getWidth(), 0.25f, 1);
        scl = Math.min(scl, Mathf.clamp((main.parent.getHeight()*0.8f - Scl.scl(20)) / main.getHeight(), 0.25f, 1));
        if (lastZoom <= 0) {
          main.setScale(scl);
        }
        else main.setScale(Mathf.clamp(lastZoom, 0.25f, scl));
        main.setOrigin(Align.center);
        main.setTransform(true);

        main.setPosition(main.parent.getWidth()/2, main.parent.getHeight()/2, Align.center);
        clamp(main);
      };
    });

    recipesTable.clearChildren();
    recipesTable.fill(t -> t.table(clip -> {
      clip.setClip(true);
      clip.addChild(currZoom);
    }).grow().pad(8));
    recipesTable.table(top -> {
      top.table(t -> {
        t.table(Tex.buttonTrans).size(90).get().image(currentSelect.icon()).size(60).scaling(Scaling.fit);
        t.row();
        t.add(Core.bundle.get("dialog.recipes.currSelected")).growX().fillY().color(Color.lightGray).wrap().get().setAlignment(Align.center);
      });
      top.table(infos -> {
        infos.left().top().defaults().left();
        infos.add(currentSelect.localizedName()).color(Pal.accent);
        infos.row();
        infos.add(currentSelect.name()).color(Color.gray);

        if (currentSelect.locked()){
          infos.row();
          infos.add(Core.bundle.get("dialog.recipes.locked")).color(Color.gray);
        }
      }).grow().padLeft(12).padTop(8);
    }).left().growX().fillY().pad(8);
    recipesTable.row();
    recipesTable.add().grow();
    recipesTable.row();
    recipesTable.table(bu -> {
      bu.button(Icon.add, Styles.clearNonei, 36, () -> {
        TooManyItems.calculatorDialog.addRecipe(recipes.get(recipeIndex));
        if (!TooManyItems.recipesDialog.isShown()) TooManyItems.calculatorDialog.show();
        hide();
      }).margin(5).disabled(b -> {
        Recipe r = recipes.get(recipeIndex);
        if (r.recipeType == RecipeType.building) return true;
        boolean ba = false;
        for (RecipeItem<?> key : r.productions.keys()) {
          if (!((GeneratorRecipe) RecipeType.generator).isPower(key)) ba = true;
        }
        return !ba;
      });
    });
    recipesTable.row();
    recipesTable.table(butt -> {
      butt.button(Icon.leftOpen, Styles.clearNonei, 32, () -> {
        recipeIndex--;
        rebuildRecipe.run();
      }).disabled(b -> recipeIndex <= 0).size(45);
      butt.button("<<", Styles.cleart, () -> {
        recipeIndex = 0;
        rebuildRecipe.run();
      }).disabled(b -> recipeIndex <= 0).size(45).get().getStyle().disabled = Styles.none;
      butt.table(t -> {
        t.touchable = Touchable.enabled;
        Boolc[] buildPage = new Boolc[1];
        buildPage[0] = b -> {
          t.clear();

          t.hovered(() -> Core.graphics.cursor(Graphics.Cursor.SystemCursor.hand));
          t.exited(() -> Core.graphics.restoreCursor());

          if (b){
            GlyphLayout l = GlyphLayout.obtain();
            int i = Mathf.ceil(Mathf.log(itemPages, 10));
            StringBuilder s = new StringBuilder();
            for (int n = 0; n < i; n++) {
              s.append("0");
            }
            l.setText(Fonts.def,  s.toString());

            t.add(Core.bundle.get("dialog.recipes.jump_a"));
            t.field(
                Integer.toString(recipeIndex + 1),
                (field, c) -> Character.isDigit(c) && Integer.parseInt(field.getText() + c) > 0 && Integer.parseInt(field.getText() + c) <= recipeViews.size,
                st -> {
                  recipeIndex = st.isEmpty()? 0: Integer.parseInt(st) - 1;
                  rebuildRecipe.run();
                }
            ).width(l.width + 45);
            t.add(Core.bundle.format("dialog.recipes.jump_b", recipeViews.size));
            t.update(() -> {
              if (Core.input.justTouched() && Core.scene.hit(Core.input.mouseX(), Core.input.mouseY(), true).parent != t) buildPage[0].get(false);
            });

            l.free();
          }
          else {
            t.add("").update(l -> {
              l.setAlignment(Align.center);
              l.setText(Core.bundle.format("dialog.recipes.pages", recipeIndex + 1, recipeViews.size));
            }).growX();
            t.clicked(() -> buildPage[0].get(true));
          }
        };

        buildPage[0].get(false);
      }).growX();
      butt.button(">>", Styles.cleart, () -> {
        recipeIndex = recipeViews.size - 1;
        rebuildRecipe.run();
      }).disabled(b -> recipeIndex >= recipeViews.size - 1).size(45).get().getStyle().disabled = Styles.none;
      butt.button(Icon.rightOpen, Styles.clearNonei, 32, () -> {
        recipeIndex++;
        rebuildRecipe.run();
      }).disabled(b -> recipeIndex >= recipeViews.size - 1).size(45);
    }).pad(8).growX().fillY();

    Core.app.post(rebuildRecipe);

    return true;
  }

  private void clamp(Table currZoom) {
    Group par = currZoom.parent;
    if (par == null) return;

    float zoomW = currZoom.getWidth()*currZoom.scaleX;
    float zoomH = currZoom.getHeight()*currZoom.scaleY;
    float zoomX = currZoom.x + currZoom.getWidth()/2;
    float zoomY = currZoom.y + currZoom.getHeight()/2;

    float originX = par.getWidth()/2;
    float originY = par.getHeight()/2;

    float diffX = zoomX - originX;
    float diffY = zoomY - originY;

    float maxX, maxY;
    if (par.getWidth() > zoomW) maxX = (par.getWidth() - zoomW) / 2.1f;
    else maxX = (zoomW - par.getWidth())/2f;

    if (par.getHeight() > zoomH) maxY = (par.getHeight() - zoomH)/2.1f;
    else maxY = (zoomH - par.getHeight())/2f;

    float cx = Mathf.clamp(diffX, -maxX, maxX);
    float cy = Mathf.clamp(diffY, -maxY, maxY);

    currZoom.setPosition(originX + cx, originY + cy, Align.center);
  }

  private void buildItem(Table t, RecipeItem<?> content) {
    t.add(new Table(){
      float progress, alpha;
      boolean activity, touched;
      float time;
      int clicked;
      
      {
        defaults().padLeft(8).padRight(8);

        hovered(() -> activity = true);
        exited(() -> activity = false);
        tapped(() -> {
          touched = true;
          time = Time.globalTime;
        });
        released(() -> {
          touched = false;

          if (Time.globalTime - time < 12){
            if (!mobile || Core.settings.getBool("keyboard")) {
              TooManyItems.recipesDialog.setCurrSelecting(content, Core.input.keyDown(binds.hotKey)? content.item instanceof Block && TooManyItems.recipesManager.getRecipesByFactory(content).any()? Mode.factory: Mode.usage: Mode.recipe);
            }
            else {
              clicked++;
              TooManyItems.recipesDialog.setCurrSelecting(content, clicked%2 == 0? content.item instanceof Block && TooManyItems.recipesManager.getRecipesByFactory(content).any()? Mode.factory: Mode.usage: Mode.recipe);
            }
          }
          else {
            if (content.hasDetails() && progress >= 0.95f){
              content.displayDetails();
            }
          }
        });

        update(() -> {
          alpha = Mathf.lerpDelta(alpha, currentSelect == content || touched || activity ? 1 : 0, 0.08f);
          progress = Mathf.approachDelta(progress, content.hasDetails() && touched? 1 : 0, 1/60f);
          if (clicked > 0 && Time.globalTime - time > 12) clicked = 0;
        });
        add(new Element(){
          final float elemWidth;
          final float elemHeight;

          {
            GlyphLayout layout = GlyphLayout.obtain();
            layout.setText(Fonts.outline, content.localizedName());

            elemWidth = layout.width*Scl.scl();
            elemHeight = layout.height*Scl.scl();

            layout.free();
          }

          @Override
          public void draw() {
            super.draw();

            float backWidth = elemWidth + Scl.scl(12), backHeight = height;
            Draw.color(Color.lightGray, 0.25f*alpha);
            Fill.rect(x + width/2, y + height/2, backWidth*progress, backHeight);

            Fonts.outline.draw(content.localizedName(), x + width/2, y + backHeight/2 + elemHeight/2, Tmp.c1.set(Color.white).a(alpha), 1, false, Align.center);
          }
        }).height(35);
        row();

        if (content.locked()){
          stack(
              new Image(content.icon()).setScaling(Scaling.fit),
              new Table(t -> {
                t.right().bottom().defaults().right().bottom().pad(4);
                t.image(Icon.lock).scaling(Scaling.fit).size(10).color(Color.lightGray);
              })
          ).grow().padBottom(10);
        }
        else {
          image(content.icon()).scaling(Scaling.fit).grow().padBottom(10);
        }
      }

      @Override
      protected void drawBackground(float x, float y) {
        if (currentSelect == content){
          Draw.color(Color.darkGray, parentAlpha);
          Fill.rect(x + width/2, y + height/2, width, height);
        }
        else if(activity){
          Draw.color(Color.lightGray, parentAlpha);
          Lines.stroke(4);
          Lines.line(x + 8, y + 2, x + width - 8, y + 2);
        }
        else super.drawBackground(x, y);
      }
    });
  }

  public void setCurrSelecting(RecipeItem<?> content, Mode mode) {
    if (currentSelect == content && mode == recipeMode) return;
    RecipeItem<?> old = currentSelect;
    Mode oldMode = recipeMode;

    currentSelect = content;
    recipeMode = mode;
    if (currentSelect == null) return;
    if (!buildRecipes()){
      currentSelect = old;
      recipeMode = oldMode;

      Vars.ui.showInfoFade(Core.bundle.get("dialog.recipes.no_" + (mode == Mode.recipe? "recipe": "usage")));
    }
  }

  public void setCurrSelecting(RecipeItem<?> content) {
    if (currentSelect == content) return;
    RecipeItem<?> old = currentSelect;

    currentSelect = content;
    if (currentSelect == null) return;
    if (!buildRecipes()){
      currentSelect = old;
    }
  }

  public void setRecipeMode(Mode mode){
    if (mode == recipeMode) return;
    Mode oldMode = recipeMode;

    recipeMode = mode;
    if (!buildRecipes()){
      recipeMode = oldMode;
    }
  }

  public void show(RecipeItem<?> content) {
    recipeMode = null;
    currentSelect = content;
    currPage = -1;
    show();
  }

  public static class Sorting{
    public String localized;
    public Drawable icon;
    public Comparator<RecipeItem<?>> sort;
  }

  public enum Mode{
    usage {
      @Override
      public Drawable icon() {
        return Icon.info;
      }
    },
    recipe {
      @Override
      public Drawable icon() {
        return Icon.tree;
      }
    },
    factory {
      @Override
      public Drawable icon() {
        return Icon.production;
      }
    };

    public String localized() {
      return Core.bundle.get("dialog.recipes.mode_" + name());
    }

    public abstract Drawable icon();
  }
}
