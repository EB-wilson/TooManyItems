package tmi.ui;

import arc.Core;
import arc.func.Prov;
import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.event.Touchable;
import arc.scene.style.Drawable;
import arc.scene.ui.Button;
import arc.scene.ui.ImageButton;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Collapser;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.*;
import mindustry.Vars;
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
import tmi.util.Consts;

import java.text.Collator;
import java.util.Comparator;

import static tmi.util.Consts.*;

public class RecipesDialog extends BaseDialog {
  private final static Collator compare = Collator.getInstance(Core.bundle.getLocale());

  public Seq<Sorting> sortings = Seq.with(new Sorting(){{
    localized = Core.bundle.get("misc.defaultSort");
    icon = Icon.menu;
    sort = (a, b) -> {
      int n = a.getContentType().compareTo(b.getContentType());

      if (n == 0){
        return a.id - b.id;
      }

      return n;
    };
  }}, new Sorting(){{
    localized = Core.bundle.get("misc.nameSort");
    icon = a_z;
    sort = (a, b) -> compare.compare(a.localizedName, b.localizedName);
  }}, new Sorting(){{
    localized = Core.bundle.get("misc.modSort");
    icon = Icon.book;
    sort = (a, b) -> a.minfo.mod == null? b.minfo.mod == null? 0: 1: b.minfo.mod != null? compare.compare(a.minfo.mod.name, b.minfo.mod.name): -1;
  }}, new Sorting(){{
    localized = Core.bundle.get("misc.typeSort");
    icon = Icon.file;
    sort = (a, b) -> {
      int n = a.getContentType().compareTo(b.getContentType());

      if (n == 0){
        if (a instanceof Block ba && b instanceof Block bb){
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
  ScrollPane contentPane;
  UnlockableContent currentSelect;

  @Nullable Mode recipeMode = null;

  String contentSearch = "";
  Sorting sorting = sortings.first();
  boolean reverse;
  int fold, recipeIndex;

  Seq<UnlockableContent> ucSeq = new Seq<>();

  Runnable contentsRebuild, rebuildRecipe;

  public RecipesDialog() {
    super(Core.bundle.get("dialog.recipes.title"));

    addCloseButton();

    shown(this::buildBase);
    resized(this::buildBase);
  }

  protected void buildBase() {
    cont.clearChildren();

    if (Core.graphics.isPortrait()){
      recipesTable = cont.table(padGrayUI).grow().pad(5).get();
      cont.row();

      Collapser coll = new Collapser(t -> contentsTable = t.table(padGrayUI).growX().height(Core.graphics.getHeight()/2f).get(), true).setDuration(0.5f);
      Table tab = new Table(Consts.grayUI, ta -> ta.add(coll).growX().fillY());
      cont.addChild(tab);
      contentsTable.setSize(tab.parent.getWidth(), tab.getPrefHeight());

      cont.button(Icon.up, Styles.clearNonei, 32, () -> {
        coll.setCollapsed(!coll.isCollapsed(), true);
      }).growX().height(40).update(i -> {
        i.getStyle().imageUp = coll.isCollapsed() ? Icon.upOpen : Icon.downOpen;
        tab.setSize(tab.parent.getWidth(), tab.getPrefHeight());
        tab.setPosition(i.x, i.y + i.getPrefHeight() + 4, Align.bottomLeft);
      });
    }
    else {
      recipesTable = cont.table(padGrayUI).grow().pad(5).get();
      cont.image().color(Pal.accent).growY().pad(0).width(4);
      contentsTable = cont.table(padGrayUI).growY().width(Core.graphics.getWidth()/2.5f).pad(5).get();
    }

    buildContents();
    buildRecipes();
  }

  protected void buildContents() {
    cont.layout();

    float width = contentsTable.getWidth() - 36;

    contentsTable.table(filter -> {
      filter.add(Core.bundle.get("misc.search"));
      filter.image(Icon.zoom).size(36).scaling(Scaling.fit);
      filter.field(contentSearch, str -> {
        contentSearch = str.toLowerCase();
        contentsRebuild.run();
      }).growX();

      sortingTab = new Table(grayUI, ta -> {
        for (Sorting sort : sortings) {
          ta.button(t -> {
            t.defaults().left().pad(5);
            t.image(sort.icon).size(24).scaling(Scaling.fit);
            t.add(sort.localized).growX();
          }, Styles.clearNoneTogglei, () -> {
            sorting = sort;
            contentsRebuild.run();
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
        contentsRebuild.run();
      }).size(36);
      filter.add("").update(l -> l.setText(Core.bundle.get(reverse? "dialog.unitFactor.reverse": "dialog.unitFactor.order"))).color(Pal.accent);
    }).padBottom(12).growX();
    contentsTable.row();
    contentPane = contentsTable.top().pane(Styles.smallPane, t -> {
      contentsRebuild = () -> {
        t.clearChildren();
        t.left().top().defaults().size(60, 90);
        float curWidth = 0;

        ucSeq.clear();
        for (ContentType type : ContentType.all) {
          Vars.content.getBy(type).each(e -> {
            if (e instanceof UnlockableContent uc && TooManyItems.recipesManager.anyRecipe(uc)) ucSeq.add(uc);
          });
        }

        if (reverse) ucSeq.sort((a, b) -> sorting.sort.compare(b, a));
        else ucSeq.sort(sorting.sort);

        fold = 0;
        for (UnlockableContent content : ucSeq) {
          if (!content.localizedName.toLowerCase().contains(contentSearch)  && !content.name.toLowerCase().contains(contentSearch)){
            fold++;
            continue;
          }

          buildItem(t, content);

          curWidth += 60;
          if (curWidth + 60 >= width){
            t.row();
            curWidth = 0;
          }
        }
      };

      contentsRebuild.run();
    }).growX().fillY().get();

    contentsTable.row();
    contentsTable.add("").color(Color.gray).left().growX().update(l -> l.setText(Core.bundle.format("dialog.recipes.total", ucSeq.size - fold, fold)));

    contentsTable.addChild(sortingTab);
  }

  protected boolean buildRecipes() {
    Seq<Recipe> recipes;

    if (currentSelect != null && !(currentSelect instanceof Block) && recipeMode == Mode.factory) recipeMode = null;

    if (currentSelect == null){
      recipes = null;
    }
    else {
      if (recipeMode == null) {
        recipeMode = TooManyItems.recipesManager.anyMaterial(currentSelect) ? Mode.usage :
            TooManyItems.recipesManager.anyProduction(currentSelect) ? Mode.recipe :
            currentSelect instanceof Block ? Mode.factory : null;
      }

      recipes = recipeMode == null? null: switch (recipeMode) {
        case usage -> TooManyItems.recipesManager.getRecipesByMaterial(currentSelect);
        case recipe -> TooManyItems.recipesManager.getRecipesByProduction(currentSelect);
        case factory -> TooManyItems.recipesManager.getRecipesByFactory((Block) currentSelect);
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
          RecipeView view = new RecipeView(recipe);
          recipeViews.add(view);
        }
      }
    }

    if (recipes == null || recipeViews.isEmpty()) return false;

    recipesTable.clearChildren();
    recipesTable.table(top -> {
      top.table(t -> {
        t.table(Tex.buttonTrans).size(90).get().image(currentSelect.uiIcon).size(60).scaling(Scaling.fit);
        t.row();
        t.add(Core.bundle.get("dialog.recipes.currSelected")).growX().color(Color.lightGray).get().setAlignment(Align.center);
      });
      top.table(infos -> {
        infos.left().top().defaults().left();
        infos.add(currentSelect.localizedName).color(Pal.accent);
        infos.row();
        infos.add(currentSelect.name).color(Color.gray);
      }).grow().padLeft(12).padTop(8);
    }).left().growX().fillY().pad(8);
    recipesTable.row();

    recipesTable.pane(p -> p.table(main -> {
      recipeIndex = 0;
      rebuildRecipe = () -> {
        main.clearChildren();
        RecipeView view = recipeViews.get(recipeIndex);
        view.layout();
        main.add(view).fill();
      };
      rebuildRecipe.run();
    }).grow()).grow().pad(8).center();
    recipesTable.row();
    recipesTable.table(butt -> {
      butt.button(Icon.leftOpen, Styles.clearNonei, 32, () -> {
        recipeIndex--;
        rebuildRecipe.run();
      }).disabled(b -> recipeIndex <= 0).size(45);
      butt.table(modes -> {
        modeTab = new Table(grayUI, ta -> {
          for (Mode mode : Mode.values()) {
            if (mode == Mode.factory && !(currentSelect instanceof Block)) continue;
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
          image().scaling(Scaling.fit).size(32).update(i -> i.setDrawable(recipeMode.icon()));
          add("").padLeft(4).update(l -> l.setText(recipeMode.localized()));

          clicked(() -> modeTab.visible = !modeTab.visible);

          update(() -> {
            modeTab.setSize(modeTab.getPrefWidth(), modeTab.getPrefHeight());
            modeTab.setPosition(x, y + getHeight(), Align.bottomLeft);
          });
        }}).margin(8).fill().get();

        modes.addChild(modeTab);
      });
      butt.add("").update(l -> {
        l.setAlignment(Align.center);
        l.setText(Core.bundle.format("dialog.recipes.pages", recipeIndex + 1, recipeViews.size));
      }).growX();
      butt.button(Icon.rightOpen, Styles.clearNonei, 32, () -> {
        recipeIndex++;
        rebuildRecipe.run();
      }).disabled(b -> recipeIndex >= recipeViews.size - 1).size(45);
    }).pad(8).growX().fillY();

    return true;
  }

  private void buildItem(Table t, UnlockableContent content) {
    t.add(new Table(){
      float progress, alpha;
      boolean activity, touched;
      float time;
      
      {
        touchable = Touchable.enabled;

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
            setCurrSelecting(content, Core.input.ctrl()? content instanceof Block b && TooManyItems.recipesManager.getRecipesByFactory(b).any()? Mode.factory: Mode.usage: Mode.recipe);
          }
          else {
            if (progress >= 0.92f) Vars.ui.content.show(content);
          }
        });

        update(() -> {
          alpha = Mathf.lerpDelta(alpha, currentSelect == content || touched || activity ? 1 : 0, 0.08f);
          progress = Mathf.approachDelta(progress, touched? 1 : 0, 1/60f);
        });
        add(new Element(){
          final float elemWidth;
          final float elemHeight;

          {
            GlyphLayout layout = GlyphLayout.obtain();
            layout.setText(Fonts.outline, content.localizedName);

            elemWidth = layout.width;
            elemHeight = layout.height;

            layout.free();
          }

          @Override
          public void draw() {
            super.draw();

            float backWidth = elemWidth + 12, backHeight = height;
            Draw.color(Color.lightGray, 0.25f*alpha);
            Fill.rect(x + width/2, y + height/2, backWidth*progress, backHeight);

            Fonts.outline.draw(content.localizedName, x + width/2, y + backHeight/2 + elemHeight/2, Tmp.c1.set(Color.white).a(alpha), 1, false, Align.center);
          }
        }).height(35);
        row();
        image(content.uiIcon).grow().scaling(Scaling.fit).padBottom(10);
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

  public void setCurrSelecting(UnlockableContent content, Mode mode) {
    if (currentSelect == content && mode == recipeMode) return;
    UnlockableContent old = currentSelect;
    Mode oldMode = recipeMode;

    currentSelect = content;
    recipeMode = mode;
    if (!buildRecipes()){
      currentSelect = old;
      recipeMode = oldMode;
    }
  }

  public void setCurrSelecting(UnlockableContent content) {
    if (currentSelect == content) return;
    UnlockableContent old = currentSelect;

    currentSelect = content;
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

  public static class Sorting{
    public String localized;
    public Drawable icon;
    public Comparator<UnlockableContent> sort;
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
