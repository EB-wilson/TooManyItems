package singularity.ui.dialogs;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.GlyphLayout;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.event.Touchable;
import arc.scene.style.Drawable;
import arc.scene.ui.ImageButton;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Scaling;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.ctype.ContentType;
import mindustry.ctype.UnlockableContent;
import mindustry.gen.Icon;
import mindustry.graphics.Pal;
import mindustry.ui.Fonts;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import singularity.Sgl;
import singularity.graphic.SglDrawConst;

import java.text.Collator;
import java.util.Comparator;

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
    icon = SglDrawConst.a_z;
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

  Table recipesTable, contentsTable, sortingTab;
  ScrollPane contentPane;
  UnlockableContent currentSelect;

  String contentSearch = "";
  Sorting sorting = sortings.first();
  boolean reverse;
  int fold;

  Seq<UnlockableContent> ucSeq = new Seq<>();

  Runnable contentsRebuild;

  public RecipesDialog() {
    super(Core.bundle.get("dialog.recipes.title"));

    addCloseButton();

    shown(this::buildBase);
    resized(this::buildBase);
  }

  protected void buildBase() {
    cont.clearChildren();

    if (Core.graphics.isPortrait()){
      recipesTable = cont.table(SglDrawConst.padGrayUI).growX().height(Core.graphics.getHeight()/2f).pad(5).get();
      cont.row();
      cont.image().color(Pal.accent).growX().pad(0).height(4);
      cont.row();
      contentsTable = cont.table(SglDrawConst.padGrayUI).grow().pad(5).get();
    }
    else {
      recipesTable = cont.table(SglDrawConst.padGrayUI).grow().pad(5).get();
      cont.image().color(Pal.accent).growY().pad(0).width(4);
      contentsTable = cont.table(SglDrawConst.padGrayUI).growY().width(Core.graphics.getWidth()/2.5f).pad(5).get();
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

      sortingTab = new Table(SglDrawConst.grayUI, ta -> {
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
            if (e instanceof UnlockableContent uc && Sgl.recipes.anyRecipe(uc)) ucSeq.add(uc);
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

  protected void buildRecipes() {

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
            currentSelect = content;
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
            Draw.color(SglDrawConst.matrixNet, 0.25f*alpha);
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

  public static class Sorting{
    public String localized;
    public Drawable icon;
    public Comparator<UnlockableContent> sort;
  }
}
