package tmi.ui;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.event.Touchable;
import arc.scene.style.Drawable;
import arc.scene.ui.Button;
import arc.scene.ui.Image;
import arc.scene.ui.ImageButton;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.*;
import mindustry.Vars;
import mindustry.ctype.Content;
import mindustry.ctype.ContentType;
import mindustry.ctype.UnlockableContent;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.ui.Fonts;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import rhino.Sorting;
import tmi.TooManyItems;
import tmi.recipe.Recipe;
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
    sort = (a, b) -> {
      int n = Integer.compare(a.typeID(), b.typeID());

      if (n == 0){
        return a.ordinal() - b.ordinal();
      }

      return n;
    };
  }}, new Sorting(){{
    localized = Core.bundle.get("misc.nameSort");
    icon = a_z;
    sort = (a, b) -> compare.compare(a.localizedName(), b.localizedName());
  }}, new Sorting(){{
    localized = Core.bundle.get("misc.modSort");
    icon = Icon.book;
    sort = (a, b) -> {
      if (a.item instanceof Content ca && b.item instanceof Content cb) {
        return ca.minfo.mod == null ? cb.minfo.mod == null ? 0 : 1 : cb.minfo.mod != null ? compare.compare(ca.minfo.mod.name, cb.minfo.mod.name) : -1;
      }
      else return 1;
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
      sorting = sortings.first();
      cont.clear();
    });

    scrolled(s -> {
      if (s < 0 && currPage > 0){
        currPage--;
        contentsRebuild.run();
      }
      else if (s > 0 && currPage < itemPages - 1){
        currPage++;
        contentsRebuild.run();
      }
    });
  }

  protected void buildBase() {
    cont.clearChildren();

    if (Core.graphics.isPortrait()){
      recipesTable = cont.table(padGrayUI).grow().pad(5).get();
      cont.row();

      Table tab = new Table(Consts.grayUI, t -> contentsTable = t.table(padGrayUI).growX().height(Core.graphics.getHeight()/2f/Scl.scl()).get()){
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
      recipesTable = cont.table(padGrayUI).grow().pad(5).get();
      cont.image().color(Pal.accent).growY().pad(0).width(4);
      contentsTable = cont.table(padGrayUI).growY().width(Core.graphics.getWidth()/2.5f/Scl.scl()).pad(5).get();
    }

    buildContents();
    buildRecipes();
  }

  protected void buildContents() {
    contentsTable.table(filter -> {
      filter.add(Core.bundle.get("misc.search"));
      filter.image(Icon.zoom).size(36).scaling(Scaling.fit);
      filter.field(contentSearch, str -> {
        contentSearch = str.toLowerCase();
        refreshSeq.run();
      }).growX();

      sortingTab = new Table(grayUI, ta -> {
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
      butt.add("").update(l -> {
        l.setAlignment(Align.center);
        l.setText(Core.bundle.format("dialog.recipes.pages", currPage + 1, itemPages));
      }).growX();
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
      ObjectSet<Seq<Recipe>> groups = new ObjectSet<>();
      for (Recipe recipe : recipes) {
        Seq<Recipe> group = TooManyItems.recipesManager.getRecipeGroup(recipe);
        if (group != null){
          if (!groups.add(group)) continue;

        }
        else {
          RecipeView view = new RecipeView(recipe, this::setCurrSelecting);
          recipeViews.add(view);
        }
      }
    }

    if (recipes == null || recipeViews.isEmpty()) return false;

    recipesTable.clearChildren();
    recipesTable.table(top -> {
      top.table(t -> {
        t.table(Tex.buttonTrans).size(90).get().image(currentSelect.icon()).size(60).scaling(Scaling.fit);
        t.row();
        t.add(Core.bundle.get("dialog.recipes.currSelected")).growX().color(Color.lightGray).get().setAlignment(Align.center);
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

    recipesTable.table(modes -> {
      modeTab = new Table(grayUI, ta -> {
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
      modes.add(new Button(Styles.clearNonei){{
        touchable = modeTab.getChildren().size > 1? Touchable.enabled: Touchable.disabled;

        image().scaling(Scaling.fit).size(32).update(i -> i.setDrawable(recipeMode.icon()));
        add("").padLeft(4).update(l -> l.setText(recipeMode.localized()));

        clicked(() -> modeTab.visible = !modeTab.visible);

        update(() -> {
          modeTab.setSize(modeTab.getPrefWidth(), modeTab.getPrefHeight());
          modeTab.setPosition(modes.x + x + width/2, modes.y, Align.top);
        });
      }}).margin(8).fill().get();

    }).fill();
    recipesTable.row();

    recipesTable.pane(p -> p.table(main -> {
      recipeIndex = 0;
      rebuildRecipe = () -> {
        main.clearChildren();
        RecipeView view = recipeViews.get(recipeIndex);
        view.validate();
        main.add(view).fill();
        main.row();
        main.table(t -> {
          t.center().defaults().center();
          if (view.recipe.subInfoBuilder != null){
            view.recipe.subInfoBuilder.get(t);
          }
        }).fill().padTop(8);
      };
      rebuildRecipe.run();
    }).grow()).grow().pad(8).center();
    recipesTable.row();
    recipesTable.table(butt -> {
      butt.button(Icon.leftOpen, Styles.clearNonei, 32, () -> {
        recipeIndex--;
        rebuildRecipe.run();
      }).disabled(b -> recipeIndex <= 0).size(45);
      butt.add("").update(l -> {
        l.setAlignment(Align.center);
        l.setText(Core.bundle.format("dialog.recipes.pages", recipeIndex + 1, recipeViews.size));
      }).growX();
      butt.button(Icon.rightOpen, Styles.clearNonei, 32, () -> {
        recipeIndex++;
        rebuildRecipe.run();
      }).disabled(b -> recipeIndex >= recipeViews.size - 1).size(45);
    }).pad(8).growX().fillY();

    recipesTable.addChild(modeTab);

    return true;
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
          time = Time.time;
        });
        released(() -> {
          touched = false;

          if (Time.time - time < 12){
            if (!mobile || Core.settings.getBool("keyboard")) {
              TooManyItems.recipesDialog.setCurrSelecting(content, Core.input.keyDown(binds.hotKey)? content.item instanceof Block b && TooManyItems.recipesManager.getRecipesByFactory(content).any()? Mode.factory: Mode.usage: Mode.recipe);
            }
            else {
              clicked++;
              TooManyItems.recipesDialog.setCurrSelecting(content, clicked%2 == 0? content.item instanceof Block b && TooManyItems.recipesManager.getRecipesByFactory(content).any()? Mode.factory: Mode.usage: Mode.recipe);
            }
          }
          else {
            if (content.item instanceof UnlockableContent c && progress >= 0.95f){
              Vars.ui.content.show(c);
            }
          }
        });

        update(() -> {
          alpha = Mathf.lerpDelta(alpha, currentSelect == content || touched || activity ? 1 : 0, 0.08f);
          progress = Mathf.approachDelta(progress, content.item instanceof UnlockableContent && touched? 1 : 0, 1/60f);
          if (clicked > 0 && Time.time - time > 12) clicked = 0;
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

            Fonts.outline.draw(content.localizedName(), x + width/2, y + backHeight/2 + elemHeight/2, Tmp.c1.set(Color.white).a(alpha), Scl.scl(), false, Align.center);
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
