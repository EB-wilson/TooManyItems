package tmi.util;

import arc.Core;
import arc.func.Prov;
import arc.graphics.Color;
import arc.scene.style.Drawable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.Dialog;
import arc.struct.Seq;
import arc.util.Tmp;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.ui.Fonts;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Floor;

public class Consts {
  private static final Seq<?> emp = new Seq<>();

  public static Drawable grayUI, darkGrayUI, grayUIAlpha, darkGrayUIAlpha, padGrayUIAlpha, a_z, tmi, transparent;

  public static Dialog.DialogStyle transparentBack;

  public static Tile markerTile;

  public static void load(){
    a_z = Core.atlas.getDrawable("tmi-a_z");
    tmi = Core.atlas.getDrawable("tmi-tmi");

    grayUI = ((TextureRegionDrawable) Tex.whiteui).tint(Tmp.c1.set(Pal.darkerGray));
    darkGrayUI = ((TextureRegionDrawable) Tex.whiteui).tint(Tmp.c1.set(Pal.darkestGray));
    grayUIAlpha = ((TextureRegionDrawable) Tex.whiteui).tint(Tmp.c1.set(Pal.darkerGray).a(0.7f));
    darkGrayUIAlpha = ((TextureRegionDrawable) Tex.whiteui).tint(Tmp.c1.set(Pal.darkestGray).a(0.7f));
    padGrayUIAlpha = ((TextureRegionDrawable) Tex.whiteui).tint(Tmp.c1.set(Pal.darkerGray).a(0.7f));
    padGrayUIAlpha.setLeftWidth(8);
    padGrayUIAlpha.setRightWidth(8);
    padGrayUIAlpha.setTopHeight(8);
    padGrayUIAlpha.setBottomHeight(8);
    transparent = ((TextureRegionDrawable) Tex.whiteui).tint(Color.clear);

    transparentBack = new Dialog.DialogStyle(){{
      stageBackground = transparent;
      titleFont = Fonts.outline;
      background = transparent;
      titleFontColor = Pal.accent;
    }};

    markerTile = new Tile(0, 0){
      @Override
      public void setFloor(Floor type) {
        this.floor = type;
        this.overlay = (Floor) Blocks.air;
      }

      @Override
      public void setOverlay(Block block) {
        this.overlay = (Floor) block;
      }

      @Override
      public void setBlock(Block type, Team team, int rotation, Prov<Building> entityprov) {
        this.block = type;
        this.build = entityprov.get();
        this.build.team = team;
        this.build.rotation = rotation;
      }
    };
  }

  @SuppressWarnings("unchecked")
  public static <T> Seq<T> empSeq() {
    return (Seq<T>) emp;
  }
}
