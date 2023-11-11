package tmi.util;

import arc.Core;
import arc.func.Prov;
import arc.scene.style.Drawable;
import arc.scene.style.TextureRegionDrawable;
import arc.struct.Seq;
import arc.util.Tmp;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Floor;
import tmi.recipe.Recipe;

public class Consts {
  private static final Seq<?> emp = new Seq<>();

  public static Drawable grayUI, padGrayUI, a_z, tmi;

  public static Tile markerTile;

  public static void load(){
    a_z = Core.atlas.getDrawable("tmi-a_z");
    tmi = Core.atlas.getDrawable("tmi-tmi");

    grayUI = ((TextureRegionDrawable) Tex.whiteui).tint(Tmp.c1.set(Pal.darkerGray).a(0.7f));
    padGrayUI = ((TextureRegionDrawable) Tex.whiteui).tint(Tmp.c1.set(Pal.darkerGray).a(0.7f));
    padGrayUI.setLeftWidth(8);
    padGrayUI.setRightWidth(8);
    padGrayUI.setTopHeight(8);
    padGrayUI.setBottomHeight(8);

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
