package tmi.ui;

import arc.Core;
import arc.Graphics;
import arc.files.Fi;
import arc.func.Cons;
import arc.func.Func;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.gl.FrameBuffer;
import arc.input.KeyCode;
import arc.math.Angles;
import arc.math.Interp;
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
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.CheckBox;
import arc.scene.ui.Dialog;
import arc.scene.ui.Image;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.scene.utils.Elem;
import arc.struct.*;
import arc.util.*;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.core.UI;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.input.Binding;
import mindustry.type.Item;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import mindustry.world.meta.StatUnit;
import tmi.TooManyItems;
import tmi.recipe.EnvParameter;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeItemStack;
import tmi.recipe.RecipeType;
import tmi.recipe.types.GeneratorRecipe;
import tmi.recipe.types.RecipeItem;
import tmi.util.Consts;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

import static arc.util.Align.*;
import static mindustry.Vars.*;
import static mindustry.Vars.ui;

public class SchematicDesignerDialog extends BaseDialog {
  public static final int FI_HEAD = 0xceadbf01;

  private static final Seq<ItemLinker> seq = new Seq<>();
  private static final Vec2 tmp = new Vec2();
  private static final Rect rect = new Rect();
  protected View view;
  protected final Table menuTable = new Table(){{
    visible = false;
  }};
  protected final Dialog export = new Dialog("", Consts.transparentBack){
    final FrameBuffer buffer = new FrameBuffer();
    final TextureRegion tmp = new TextureRegion(((TextureRegionDrawable) Tex.nomap).getRegion());

    float imageScale = 1;
    float boundX, boundY;
    Fi exportFile;
    boolean updated;

    {
      shown(() -> {
        view.toBuffer(buffer, boundX, boundY, imageScale);
        updated = true;
      });

      titleTable.clear();

      cont.table(Consts.darkGrayUIAlpha, t -> {
        t.table(Consts.darkGrayUI, top -> {
          top.left().add(Core.bundle.get("dialog.calculator.export")).pad(8);
        }).grow().padBottom(12);
        t.row();
        t.table(Consts.darkGrayUI, inner -> {
          inner.left().defaults().growX().fillY().pad(5);
          Image img = inner.image(tmp).scaling(Scaling.fit).fill().size(400).update(i -> {
            if (updated) {
              tmp.set(buffer.getTexture());
              tmp.flip(false, true);
              i.setDrawable(tmp);
              updated = false;
            }
          }).get();
          img.clicked(() -> {
            new BaseDialog(""){{
              cont.image(tmp).grow().pad(30).scaling(Scaling.fit);
              cont.row();
              cont.add("").color(Color.lightGray).update(l -> l.setText(Core.bundle.format("dialog.calculator.exportPrev",
                  buffer.getWidth(),
                  buffer.getHeight(),
                  Mathf.round(imageScale*100)
              )));
              titleTable.clear();
              addCloseButton();
            }}.show();
          });
          inner.row();
          inner.add("").color(Color.lightGray).update(l -> l.setText(Core.bundle.format("dialog.calculator.exportPrev",
              buffer.getWidth(),
              buffer.getHeight(),
              Mathf.round(imageScale*100)
          )));
          inner.row();
          inner.table(s -> {
            s.left().defaults().left();
            s.add(Core.bundle.get("dialog.calculator.exportBoundX"));
            s.add("").update(l -> l.setText((int)boundX + "px")).width(80).padLeft(5).color(Color.lightGray).right();
            s.slider(0, 200, 1, boundX, f -> {
              boundX = f;
              view.toBuffer(buffer, boundX, boundY, imageScale);
              updated = true;
            }).growX().padLeft(5);
            s.row();
            s.add(Core.bundle.get("dialog.calculator.exportBoundY"));
            s.add("").update(l -> l.setText((int)boundY + "px")).width(80).padLeft(5).color(Color.lightGray);
            s.slider(0, 200, 1, boundY, f -> {
              boundY = f;
              view.toBuffer(buffer, boundX, boundY, imageScale);
              updated = true;
            }).growX().padLeft(5);
          });
          inner.row();
          inner.add(Core.bundle.get("dialog.calculator.exportScale"));
          inner.row();
          inner.table(scl -> {
            scl.defaults().growX().height(45);
            int n = 0;
            for (float scale = 0.25f; scale <= 2f; scale += 0.25f) {
              n++;
              float fs = scale;
              scl.button(Mathf.round(scale*100) + "%", Styles.flatTogglet, () -> {
                imageScale = fs;
                view.toBuffer(buffer, boundX, boundY, imageScale);
                updated = true;
              }).update(b -> b.setChecked(Mathf.equal(imageScale, fs)));
              if (n%2 == 0) scl.row();
            }
          });
        }).minWidth(420).grow().margin(8);
        t.row();
        t.table(file -> {
          file.left().defaults().left().pad(4);
          file.add(Core.bundle.get("dialog.calculator.exportFile"));
          file.add("").color(Color.lightGray).ellipsis(true).growX()
              .update(l -> l.setText(exportFile == null? Core.bundle.get("misc.unset"): exportFile.absolutePath()));
          file.button(Core.bundle.get("misc.select"), Styles.cleart, () -> {
            platform.showFileChooser(false, "png", f -> {
              exportFile = f;
            });
          }).size(60, 42);
        }).width(420);
        t.row();
        t.table(buttons -> {
          buttons.right().defaults().size(92, 36).pad(6);
          buttons.button(Core.bundle.get("misc.cancel"), Styles.cleart, this::hide);
          buttons.button(Core.bundle.get("misc.export"), Styles.cleart, () -> {
            try {
              TextureRegion region = view.toImage(boundX, boundY, imageScale);
              PixmapIO.writePng(exportFile, region.texture.getTextureData().getPixmap());

              ui.showInfo(Core.bundle.get("dialog.calculator.exportSuccess"));
            } catch (Exception e) {
              ui.showException(Core.bundle.get("dialog.calculator.exportFailed"), e);
              Log.err(e);
            }
          }).disabled(b -> exportFile == null);
        }).growX();
      }).fill().margin(8);
    }
  };
  protected final Dialog balance = new Dialog("", Consts.transparentBack){
    RecipeCard currCard;

    int balanceAmount;
    boolean balanceValid;

    Runnable rebuild;

    {
      titleTable.clear();

      Cell<Table> cell = cont.table(Consts.darkGrayUIAlpha, t -> {
        t.table(Consts.darkGrayUI, top -> {
          top.left().add(Core.bundle.get("dialog.calculator.balance")).pad(8);
        }).grow().padBottom(12);
        t.row();
        t.table(Consts.darkGrayUI).grow().margin(12).get().top().pane(Styles.smallPane, inner -> {
          shown(rebuild = () -> {
            inner.clearChildren();
            inner.defaults().left().growX().fillY().pad(5);
            currCard = selects.size == 1 && selects.first() instanceof RecipeCard c? c: null;

            inner.add(Core.bundle.get("dialog.calculator.targetRec")).color(Pal.accent);
            if (currCard != null){
              currCard.calculateEfficiency();

              inner.row();
              inner.table(Tex.pane, ls -> {
                ls.pane(Styles.noBarPane, p -> {
                  p.top().left().defaults().growX().height(45).minWidth(160).left().padLeft(4).padRight(4);

                  p.add(Core.bundle.get("dialog.calculator.materials")).labelAlign(center).color(Color.lightGray);
                  p.table(Consts.grayUIAlpha).margin(6).get().add(Core.bundle.get("dialog.calculator.expectAmount")).labelAlign(center).color(Color.lightGray);
                  p.add(Core.bundle.get("dialog.calculator.configured")).labelAlign(center).color(Color.lightGray);
                  p.row();

                  for (RecipeItemStack stack : currCard.recipe.materials.values()) {
                    if (((GeneratorRecipe) RecipeType.generator).isPower(stack.item)) continue;

                    p.table(left -> {
                      left.left();
                      left.image(stack.item.icon()).size(36).scaling(Scaling.fit);
                      left.table(num -> {
                        num.left().defaults().fill().left();
                        num.add(stack.item.localizedName());
                        num.row();
                        num.table(amo -> {
                          amo.left().defaults().left();
                          String amount = stack.isAttribute?
                              stack.amount > 1000? UI.formatAmount((long) stack.amount): Integer.toString(Mathf.round(stack.amount)):
                              (stack.amount*60 > 1000? UI.formatAmount((long) (stack.amount*60)): Strings.autoFixed(stack.amount*60, 1)) + "/s";
                          amo.add(amount).color(Color.gray);
                          if (currCard.multiplier != 1 && !stack.isAttribute && !stack.isBooster) amo.add(Mathf.round(currCard.multiplier*100) + "%").padLeft(5).color(currCard.multiplier > 1? Pal.heal: Color.red);
                          amo.add("x" + currCard.mul).color(Pal.gray);
                        });
                      }).padLeft(5);
                    });

                    float amount = stack.isAttribute? stack.amount*currCard.mul: stack.isBooster? stack.amount*currCard.mul*currCard.scale*60: stack.amount*currCard.mul*currCard.multiplier*60;
                    p.table(Consts.grayUIAlpha).get().left().marginLeft(6).marginRight(6).add((amount > 1000? UI.formatAmount((long) amount): Strings.autoFixed(amount, 1)) + (stack.isAttribute? "": "/s"));

                    p.table(stat -> {
                      stat.left().defaults().left();
                      ItemLinker input = currCard.in.find(e -> e.item == stack.item);

                      if (!stack.isAttribute && input == null){
                        stat.add(Core.bundle.get("misc.noInput"));
                      }
                      else {
                        if (stack.isAttribute){
                          if (currCard.environments.getAttribute(stack.item) <= 0) stat.add(Core.bundle.get("misc.unset"));
                          else stat.add(currCard.environments.getAttribute(stack.item) + "");
                        }
                        else if (stack.optionalCons){
                          stat.table(assign -> {
                            assign.left().defaults().left();
                            if (input.isNormalized()){
                              float a = input.links.size == 1? input.links.orderedKeys().first().expectAmount: -1;

                              assign.add(Core.bundle.get("misc.provided") + (a <= 0? "": " [lightgray]" + (a*60 > 1000? UI.formatAmount((long) (a*60)): Strings.autoFixed(a*60, 1)) + "/s")).growX();

                              assign.check("", currCard.optionalSelected.contains(stack.item), b -> {
                                if (b) currCard.optionalSelected.add(stack.item);
                                else currCard.optionalSelected.remove(stack.item);

                                currCard.rebuildConfig.run();
                                rebuild.run();
                              }).margin(4).fill();
                            }
                            else {
                              assign.add(Core.bundle.get("misc.assignInvalid")).growX();
                            }
                          }).growX();
                        }
                        else {
                          if (input.isNormalized()){
                            float a = input.links.size == 1? input.links.orderedKeys().first().expectAmount: -1;

                            stat.add(Core.bundle.get("misc.provided") + (a <= 0? "": " [lightgray]" + (a*60 > 1000? UI.formatAmount((long) (a*60)): Strings.autoFixed(a*60, 1)) + "/s"));
                          }
                          else {
                            stat.add(Core.bundle.get("misc.assignInvalid"));
                          }
                        }
                      }
                    });

                    p.row();
                  }
                }).maxHeight(240).scrollX(false).growX().fillY();
              });
              inner.row();
              inner.image(Icon.down).scaling(Scaling.fit).pad(12);
              inner.row();
              inner.table(Tex.pane, ls -> {
                ls.pane(Styles.noBarPane, p -> {
                  p.top().left().defaults().growX().height(45).minWidth(160).left().padLeft(4).padRight(4);

                  p.add(Core.bundle.get("dialog.calculator.productions")).labelAlign(center).color(Color.lightGray);
                  p.table(Consts.grayUIAlpha).margin(6).get().add(Core.bundle.get("dialog.calculator.actualAmount")).labelAlign(center).color(Color.lightGray);
                  p.add(Core.bundle.get("dialog.calculator.expectAmount")).labelAlign(center).color(Color.lightGray);
                  p.row();

                  balanceValid = false;
                  balanceAmount = 0;
                  for (RecipeItemStack stack : currCard.recipe.productions.values()) {
                    if (((GeneratorRecipe) RecipeType.generator).isPower(stack.item)) continue;

                    p.table(left -> {
                      left.left();
                      left.image(stack.item.icon()).size(36).scaling(Scaling.fit);
                      left.table(num -> {
                        num.left().defaults().fill().left();
                        num.add(stack.item.localizedName());
                        num.row();
                        num.table(amo -> {
                          amo.left().defaults().left();
                          amo.add((stack.amount*60 > 1000? UI.formatAmount((long) (stack.amount*60)): Strings.autoFixed(stack.amount*60, 1)) + "/s").color(Color.gray);
                          if (currCard.efficiency != 1) amo.add(Mathf.round(currCard.efficiency*100) + "%").padLeft(5).color(currCard.efficiency > 1? Pal.heal: Color.red);
                          amo.add("x" + currCard.mul).color(Pal.gray);
                        });
                      }).padLeft(5);
                    });

                    float[] expected = new float[1];
                    float amount = stack.amount*currCard.mul*currCard.efficiency;
                    p.table(Consts.grayUIAlpha, actual -> {
                      actual.defaults().growX().left();
                      actual.add((amount*60 > 1000? UI.formatAmount((long) (amount*60)): Strings.autoFixed(amount*60, 1)) + "/s");

                      actual.add("").update(l -> {
                        float diff = amount - expected[0];

                        if (balanceValid){
                          if (Mathf.zero(diff)){
                            l.setText(Core.bundle.get("misc.balanced"));
                            l.setColor(Color.lightGray);
                          }
                          else {
                            l.setText((diff > 0? "+": "") + (diff*60 > 1000? UI.formatAmount((long) (diff*60)): Strings.autoFixed(diff*60, 1)) + "/s");
                            l.setColor(diff > 0? Pal.accent: Color.red);
                          }
                        }
                        else {
                          l.setText("");
                        }
                      });
                    }).left().marginLeft(6).marginRight(6);

                    ItemLinker linker = currCard.out.find(e -> e.item == stack.item);
                    if (linker != null) {
                      p.table(tab -> {
                        tab.defaults().growX().fillY();
                        if (linker.links.size == 1){
                          ItemLinker other = linker.links.orderedKeys().first();
                          if (!other.isNormalized()) {
                            tab.add(Core.bundle.get("misc.assignInvalid")).color(Color.red);
                            return;
                          }

                          tab.add(Core.bundle.format("dialog.calculator.assigned", (other.expectAmount*60 > 1000? UI.formatAmount((long) (other.expectAmount*60)): Strings.autoFixed(other.expectAmount*60, 1)) + "/s"));

                          expected[0] = other.expectAmount;

                          balanceValid = true;
                          balanceAmount = Mathf.ceil(Math.max(other.expectAmount/(stack.amount*currCard.efficiency), balanceAmount));
                        }
                        else if (linker.links.isEmpty()){
                          tab.add(Core.bundle.get("misc.unset"));
                        }
                        else {
                          boolean anyUnset = false;

                          float amo = 0;
                          for (ItemLinker other : linker.links.keys()) {
                            float rate = other.links.get(linker)[0];

                            if (!other.isNormalized()){
                              anyUnset = true;
                              break;
                            }
                            else if (rate < 0) rate = 1;

                            amo += rate*other.expectAmount;
                          }

                          expected[0] = amo;

                          if (!anyUnset) {
                            tab.add(Core.bundle.format("dialog.calculator.assigned", (amo*60 > 1000? UI.formatAmount((long) (amo*60)): Strings.autoFixed(amo*60, 1)) + "/s"));
                            balanceValid = true;
                            balanceAmount = Mathf.ceil(Math.max(amo/(stack.amount*currCard.efficiency), balanceAmount));
                          }
                          else tab.add(Core.bundle.get("misc.assignInvalid")).color(Color.red);
                        }
                      });
                    }
                    else p.add("<error>");

                    p.row();
                  }
                }).maxHeight(240).scrollX(false).growX().fillY();
              }).grow();

              inner.row();
              inner.table(scl -> {
                scl.left();
                AtomicBoolean fold = new AtomicBoolean(false);
                float[] scale = new float[]{currCard.scale};
                Table tab = scl.table().growX().get();
                tab.left().defaults().left().padRight(8);
                Runnable doFold = () -> {
                  tab.clearChildren();

                  if (fold.get()) {
                    tab.add(Core.bundle.get("dialog.calculator.effScale"));
                    tab.field(Strings.autoFixed(scale[0]*100, 1), TextField.TextFieldFilter.digitsOnly, i -> {
                      try {
                        scale[0] = i.isEmpty()? 0: Float.parseFloat(i)/100;
                      } catch (Throwable ignored){}
                    }).growX().get().setAlignment(right);
                    tab.add("%").color(Color.gray);

                    fold.set(false);
                  }
                  else {
                    tab.add(Core.bundle.get("dialog.calculator.effScale") + Strings.autoFixed(currCard.scale*100, 1) + "%");

                    fold.set(true);
                  }
                };
                doFold.run();

                scl.button(Icon.pencilSmall, Styles.clearNonei, 24, () -> {
                  if (fold.get()){
                    doFold.run();
                  }
                  else {
                    currCard.scale = scale[0];
                    currCard.rebuildConfig.run();
                    rebuild.run();
                  }
                }).update(i -> i.getStyle().imageUp = fold.get()? Icon.pencilSmall: Icon.okSmall).fill().margin(5);
              });
              inner.row();
              inner.add(Core.bundle.format("dialog.calculator.expectedMultiple", balanceValid? balanceAmount + "x": Core.bundle.get("misc.invalid")));
              inner.row();
              inner.add(Core.bundle.format("dialog.calculator.currentMul", currCard.mul,
                  balanceValid? (currCard.mul == balanceAmount? "[gray]" + Core.bundle.get("misc.balanced"): (currCard.mul > balanceAmount? "[accent]+": "[red]") + (currCard.mul - balanceAmount)): ""));
            }
            else {
              inner.add("misc.unset");
            }

          });
        }).grow();
        t.row();
        t.table(buttons -> {
          buttons.right().defaults().size(92, 36).pad(6);
          buttons.button(Core.bundle.get("misc.close"), Styles.cleart, this::hide);
          buttons.button(Core.bundle.get("misc.ensure"), Styles.cleart, () -> {
            currCard.mul = balanceAmount;
            currCard.rebuildConfig.run();
            rebuild.run();
          }).disabled(b -> !balanceValid);
        }).growX();
      }).grow().margin(8);

      resized(() -> {
        cell.maxSize(Core.scene.getWidth()/Scl.scl(), Core.scene.getHeight()/Scl.scl());
        cell.get().invalidateHierarchy();
      });
    }
  };

  protected OrderedSet<Card> selects = new OrderedSet<>();

  protected Table removeArea, sideTools;
  protected boolean editLock, removeMode, selectMode;

  public SchematicDesignerDialog() {
    super("");

    titleTable.clear();

    cont.table().grow().get().add(view = new View()).grow();

    addChild(menuTable);

    hidden(() -> {
      removeMode = false;
      removeArea.setHeight(0);
      removeArea.color.a = 0;
      selectMode = false;
      selects.clear();
      editLock = false;
      hideMenu();
    });
    fill(t -> {
      t.table(c -> {
        Runnable re = () -> {
          c.clear();
          if (Core.graphics.isPortrait()) c.center().bottom().button("@back", Icon.left, SchematicDesignerDialog.this::hide).size(210, 64f);
          else c.top().right().button(Icon.cancel, Styles.flati, 32, this::hide).margin(5);
        };
        resized(re);
        re.run();
      }).grow();
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
      }).growX().top();
      t.row();
      t.add(Core.bundle.get("dialog.calculator.editLock"), Styles.outlineLabel).padTop(60).visible(() -> editLock);
    });
    fill(t -> {
      AtomicBoolean fold = new AtomicBoolean(false);
      t.right().stack(new Table(){
        Table main;
        {
          table(Tex.buttonSideLeft, but -> {
            but.touchable = Touchable.enabled;
            Image img = but.image(Icon.rightOpen).size(36).get();
            but.clicked(() -> {
              if (fold.get()) {
                clearActions();
                actions(Actions.moveTo(0, 0, 0.3f, Interp.pow2Out), Actions.run(() -> fold.set(false)));
                img.setOrigin(center);
                img.actions(Actions.rotateBy(180, 0.3f), Actions.rotateTo(0));
              }
              else {
                clearActions();
                actions(Actions.moveTo(main.getWidth(), 0, 0.3f, Interp.pow2Out), Actions.run(() -> fold.set(true)));
                img.setOrigin(center);
                img.actions(Actions.rotateBy(180, 0.3f), Actions.rotateTo(180));
              }
            });
            but.actions(Actions.run(() -> {
              img.setOrigin(center);
              img.setRotation(180);
              setPosition(main.getWidth(), 0);
              fold.set(true);
            }));
            but.hovered(() -> {
              img.setColor(Pal.accent);
              Core.graphics.cursor(Graphics.Cursor.SystemCursor.hand);
            });
            but.exited(() -> {
              img.setColor(Color.white);
              Core.graphics.restoreCursor();
            });
          }).size(36, 122).padRight(-5);
          table(Tex.buttonSideLeft, main -> {
            this.main = main;
          }).growY().width(320);
        }
      }).fillX().growY().padTop(60).padBottom(60);
    });

    fill(t -> {
      t.left().table(Consts.darkGrayUI, sideBar -> {
        sideBar.top().pane(Styles.noBarPane, list -> {
          sideTools = list;
          list.top().defaults().size(40).padBottom(8);

          list.button(Icon.add, Styles.clearNonei, 32, () -> {
            TooManyItems.recipesDialog.toggle = r -> {
              TooManyItems.recipesDialog.hide();
              addRecipe(r);
            };
            TooManyItems.recipesDialog.show();
          });
          list.row();

          list.button(Icon.refresh, Styles.clearNonei, 32, view::standardization);
          list.row();

          list.button(Icon.resize, Styles.clearNoneTogglei, 32, () -> {
            selectMode = !selectMode;
            if (!selectMode) selects.clear();
          }).update(b -> b.setChecked(selectMode));
          list.row();

          list.button(Icon.download, Styles.clearNonei, 32, () -> {
            platform.showFileChooser(true, "shd", file -> {
              try{
                view.read(file.reads());
              }catch(Exception e){
                ui.showException(e);
                Log.err(e);
              }
            });
          });
          list.row();

          list.button(Icon.save, Styles.clearNonei, 32, () -> {
            platform.showFileChooser(false, "shd", file -> {
              try{
                view.write(file.writes());
              }catch(Exception e){
                ui.showException(e);
                Log.err(e);
              }
            });
          });
          list.row();

          list.button(Icon.export, Styles.clearNonei, 32, export::show);
          list.row();

          list.button(Icon.trash, Styles.clearNoneTogglei, 32, () -> {
            removeMode = !removeMode;

            removeArea.clearActions();
            if (removeMode) {
              removeArea.actions(Actions.parallel(Actions.sizeTo(removeArea.getWidth(), Core.scene.getHeight()*0.15f, 0.12f), Actions.alpha(0.6f, 0.12f)));
            }
            else removeArea.actions(Actions.parallel(Actions.sizeTo(removeArea.getWidth(), 0, 0.12f), Actions.alpha(0, 0.12f)));
          }).update(b -> b.setChecked(removeMode));
          list.row();

          list.button(Icon.lock, Styles.clearNoneTogglei, 32, () -> {
            editLock = !editLock;
          }).update(b -> {
            b.setChecked(editLock);
            b.getStyle().imageUp = editLock? Icon.lock: Icon.lockOpen;
          });
          list.row();
        }).fill().padTop(8);

        sideBar.add().growY();

        sideBar.row();
        sideBar.button(Icon.infoCircle, Styles.clearNonei, 32, () -> {

        }).padBottom(0).size(40).padBottom(8);
      }).left().growY().fillX().padTop(100).padBottom(100);
    });
  }

  public RecipeCard addRecipe(Recipe recipe){
    RecipeCard res = new RecipeCard(recipe);
    view.addCard(res);
    res.over.visible = true;
    res.rebuildConfig.run();
    return res;
  }

  public void addIO(RecipeItem<?> item, boolean isInput) {
    view.addCard(new IOCard(item, isInput));
  }

  public void moveLock(boolean lock){
    view.lock = lock;
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

      int align = tableAlign;
      showOn.parent.localToStageCoordinates(tmp.set(v));

      if ((align & right) != 0 && tmp.x - menuTable.getWidth() < 0) align = align & ~right | left;
      if ((align & left) != 0 && tmp.x + menuTable.getWidth() > Core.scene.getWidth()) align = align & ~left | right;

      if ((align & top) != 0 && tmp.y - menuTable.getHeight() < 0) align = align & ~top | bottom;
      if ((align & bottom) != 0 && tmp.y + menuTable.getHeight() > Core.scene.getHeight()) align = align & ~bottom | top;

      showOn.parent.localToAscendantCoordinates(this, v);
      menuTable.setPosition(v.x, v.y, align);
    });

    r.run();
  }

  public void hideMenu(){
    menuTable.visible = false;
  }

  protected class View extends Group{
    ItemLinker selecting;

    final Seq<Card> cards = new Seq<>();
    final Vec2 selectBegin = new Vec2(), selectEnd = new Vec2();
    boolean isSelecting;

    boolean enabled, shown;
    float timer;

    Card newSet;
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

          Lines.stroke(Scl.scl(4), Pal.gray);
          Draw.alpha(parentAlpha);
          float gridSize = Scl.scl(Core.settings.getInt("tmi_gridSize", 150));
          for (float offX = 0; offX <= (Core.scene.getWidth())/zoom.scaleX - panX; offX += gridSize){
            Lines.line(x + offX, -Core.scene.getHeight()/zoom.scaleY, x + offX, Core.scene.getHeight()/zoom.scaleY*2);
          }
          for (float offX = 0; offX >= -(Core.scene.getWidth())/zoom.scaleX - panX; offX -= gridSize){
            Lines.line(x + offX, -Core.scene.getHeight()/zoom.scaleY, x + offX, Core.scene.getHeight()/zoom.scaleY*2);
          }

          for (float offY = 0; offY <= (Core.scene.getHeight())/zoom.scaleY - panY; offY += gridSize){
            Lines.line(-Core.scene.getWidth()/zoom.scaleX, y + offY, Core.scene.getWidth()/zoom.scaleX*2, y + offY);
          }
          for (float offY = 0; offY >= -(Core.scene.getHeight())/zoom.scaleY - panY; offY -= gridSize){
            Lines.line(-Core.scene.getWidth()/zoom.scaleX, y + offY, Core.scene.getWidth()/zoom.scaleX*2, y + offY);
          }
          super.draw();
        }
      };
      zoom.addChild(container);
      fill(t -> t.add(zoom).grow());

      update(() -> {
        if (Core.input.axis(Binding.move_x) > 0){
          panX -= 10*Time.delta/zoom.scaleX/Scl.scl();
          clamp();
        }
        else if (Core.input.axis(Binding.move_x) < 0){
          panX += 10*Time.delta/zoom.scaleX/Scl.scl();
          clamp();
        }

        if (Core.input.axis(Binding.move_y) > 0){
          panY -= 10*Time.delta/zoom.scaleY/Scl.scl();
          clamp();
        }
        else if (Core.input.axis(Binding.move_y) < 0){
          panY += 10*Time.delta/zoom.scaleY/Scl.scl();
          clamp();
        }
      });

      //left tap listener
      addCaptureListener(new ClickListener(KeyCode.mouseLeft){
        ItemLinker other;
        boolean dragged;

        @Override
        public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
          dragged = false;
          return super.touchDown(event, x, y, pointer, button);
        }

        @Override
        public void touchDragged(InputEvent event, float x, float y, int pointer) {
          dragged = true;
        }

        @Override
        public void clicked(InputEvent event, float x, float y) {
          other = null;

          if (selecting != null){
            eachCard(x, y, c -> {
              if (other != null) return;

              Vec2 v = c.stageToLocalCoordinates(tmp.set(x, y));
              other = c.hitLinker(v.x, v.y);

              if (other == selecting){

              }
              else {
                //TODO: 连接路径数据配置
              }
            }, false);
          }

          selects.clear();
          shown = false;
          isSelecting = false;
          moveLock(false);
          hideMenu();
        }

        @Override
        public boolean isOver(Element element, float x, float y) {
          return !dragged;
        }
      });

      //zoom and pan with keyboard and mouse
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

      //right tap selecting
      addCaptureListener(new InputListener(){
        Vec2 begin = new Vec2();

        @Override
        public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
          if (shown){
            hideMenu();
            shown = false;
          }
          if (!(enabled = pointer == 0 && (button == KeyCode.mouseRight || !useKeyboard()))) return false;
          timer = Time.globalTime;
          begin.set(x, y);
          return true;
        }

        @Override
        public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
          if (pointer != 0 || !((useKeyboard() && button == KeyCode.mouseRight) || (!useKeyboard() && Time.globalTime - timer > 60))) return;
          if (enabled){
            Card selecting = hitCard(x, y, true);
            if (selecting != null){
              selects.add(selecting);
            }

            buildMenu(x, y);
          }

          enabled = false;
        }

        @Override
        public void touchDragged(InputEvent event, float x, float y, int pointer) {
          if (pointer != 0) return;
          if (Mathf.dst(x - begin.x, y - begin.y) > 12) enabled = false;
        }
      });

      //zoom and pan
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
          clamp();
        }
      });

      //area selecting
      addCaptureListener(new ElementGestureListener(){
        boolean enable, panned;
        float beginX, beginY;

        final Rect rect = new Rect();

        @Override
        public void touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
          enable = selectMode || button == KeyCode.mouseRight;
          if (enable){
            if (!selectMode && !Core.input.keyDown(TooManyItems.binds.hotKey)) selects.clear();
            moveLock(true);

            beginX = x;
            beginY = y;

            selectBegin.set(selectEnd.set(x, y));
            isSelecting = true;
          }
        }

        @Override
        public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
          if ((selectMode || button == KeyCode.mouseRight)) {
            moveLock(false);
            enable = false;
            isSelecting = false;
            panned = false;

            if (!selects.isEmpty()) {
              buildMenu(x, y);
            }
          }
        }

        @Override
        public void pan(InputEvent event, float x, float y, float deltaX, float deltaY) {
          if (enable){
            if (!panned && Mathf.dst(x - beginX, y - beginY) > 14){
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

    private void buildMenu(float x, float y) {
      shown = true;
      Card selecting = hitCard(x, y, true);
      showMenu(tab -> {
        tab.table(Consts.darkGrayUIAlpha, menu -> {
          menu.defaults().growX().fillY().minWidth(300);

          if (!selects.isEmpty()){
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

            menu.button(Core.bundle.get("misc.balance"), Icon.effect, Styles.cleart, 22, () -> {
              if (selects.size == 1) {
                if (selects.first() instanceof IOCard c) {
                  if (c.isInput) {
                    ItemLinker out = c.out.first();

                    if (out.links.isEmpty()) {
                      ui.showInfo(Core.bundle.format("misc.assignInvalid"));
                      return;
                    }

                    if (extracted(c, out)) return;
                  } else {
                    ItemLinker in = c.in.first();

                    if (in == null || in.links.isEmpty()){
                      ui.showInfo(Core.bundle.format("misc.assignInvalid"));
                      return;
                    }

                    float sum = 0;
                    for (ItemLinker linker : in.links.keys()) {
                      if (!linker.isNormalized()) {
                        ui.showInfo(Core.bundle.format("misc.assignInvalid"));
                        return;
                      }

                      float rate = in.links.size == 1? 1: in.links.get(linker)[0];
                      sum += linker.expectAmount*rate;
                    }
                    c.stack.amount = sum;
                  }
                } else balance.show();
              }
              else {
                ui.showInfo("WIP");
              }

              hideMenu();
            }).margin(12).get().getLabelCell().padLeft(6).get().setAlignment(left);
            menu.row();

            menu.image().height(4).pad(4).padLeft(0).padRight(0).growX().color(Color.lightGray);
            menu.row();
          }
          if (selecting == null){
            AtomicBoolean hit = new AtomicBoolean(false);
            eachCard(x, y, c -> {
              if (hit.get()) return;

              Tmp.v1.set(x, y);
              c.stageToLocalCoordinates(Tmp.v1);
              ItemLinker linker = c.hitLinker(Tmp.v1.x, Tmp.v1.y);
              if (linker == null) return;

              if (linker.isInput) {
                menu.button(Core.bundle.get("dialog.calculator.removeLinker"), Icon.trash, Styles.cleart, 22, () -> {
                  for (ItemLinker link : linker.links.orderedKeys()) {
                    link.deLink(linker);
                  }
                  linker.remove();
                  hideMenu();
                }).margin(12).get().getLabelCell().padLeft(6).get().setAlignment(left);
                menu.row();
                menu.button(Core.bundle.get("dialog.calculator.addInputAs"), Icon.download, Styles.cleart, 22, () -> {
                  addCard(new IOCard(linker.item, true), x, y, true);
                  hideMenu();
                }).margin(12).get().getLabelCell().padLeft(6).get().setAlignment(left);
                menu.row();
              }
              else{
                menu.button(Core.bundle.get("dialog.calculator.addOutputAs"), Icon.upload, Styles.cleart, 22, () -> {
                  addCard(new IOCard(linker.item, false), x, y, true);
                  hideMenu();
                }).margin(12).get().getLabelCell().padLeft(6).get().setAlignment(left);
                menu.row();
              }

              menu.image().height(4).pad(4).padLeft(0).padRight(0).growX().color(Color.lightGray);
              menu.row();
            }, false);
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
            //addCard(new IOCard(linker.item, true), x, y, true);
            showMenu(list -> {
              list.table(Consts.darkGrayUIAlpha, items -> {
                Seq<RecipeItem<?>> l = TooManyItems.itemsManager.getList()
                    .removeAll(e -> !TooManyItems.recipesManager.anyMaterial(e) || e.item instanceof Block);
                buildItems(items, l, item -> {
                  view.addCard(new IOCard(item, true), x, y, true);
                  hideMenu();
                });
              }).update(t -> align(x, y, list, t)).margin(8);
            }, view, bottomLeft, bottomLeft, false);
          }).margin(12).get().getLabelCell().padLeft(6).get().setAlignment(left);
          menu.row();
          menu.button(Core.bundle.get("dialog.calculator.addOutput"), Icon.upload, Styles.cleart, 22, () -> {
            //addCard(new IOCard(linker.item, false), x, y, true);
            showMenu(list -> {
              list.table(Consts.darkGrayUIAlpha, items -> {
                Seq<RecipeItem<?>> l = TooManyItems.itemsManager.getList()
                    .removeAll(e -> !TooManyItems.recipesManager.anyProduction(e) || e.item instanceof Block);
                buildItems(items, l, item -> {
                  view.addCard(new IOCard(item, false), x, y, true);
                  hideMenu();
                });
              }).update(t -> align(x, y, list, t)).margin(8);
            }, view, bottomLeft, bottomLeft, false);
          }).margin(12).get().getLabelCell().padLeft(6).get().setAlignment(left);
          menu.row();
        }).update(t -> align(x, y, tab, t));

        tab.setSize(view.width, view.height);
      }, view, bottomLeft, bottomLeft, false);
    }

    private boolean extracted(IOCard c, ItemLinker out) {
      float sum = 0;
      for (ItemLinker linker : out.links.keys()) {
        if (!linker.isNormalized()) {
          ui.showInfo(Core.bundle.format("misc.assignInvalid"));
          return true;
        }

        float rate = linker.links.size == 1? 1: linker.links.get(out)[0];
        sum += linker.expectAmount*rate;
      }
      c.stack.amount = sum;
      return false;
    }

    private static void align(float x, float y, Table tab, Table t) {
      int align = 0;

      if (x + t.getWidth() > tab.getWidth()) align |= right;
      else align |= left;
      if (y - t.getHeight() < 0) align |= bottom;
      else align |= top;

      t.setPosition(x, y, align);
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

    void eachCard(float stageX, float stageY, Cons<Card> cons, boolean inner){
      Tmp.r1.set(stageX, stageY, 0, 0);
      eachCard(Tmp.r1, cons, inner);
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
      FrameBuffer buffer = toBuffer(new FrameBuffer(), boundX, boundY, scl);
      buffer.bind();
      Gl.pixelStorei(Gl.packAlignment, 1);
      int numBytes = buffer.getWidth()*buffer.getHeight()*4;
      final ByteBuffer pixels = Buffers.newByteBuffer(numBytes);
      Gl.readPixels(0, 0, buffer.getWidth(), buffer.getHeight(), Gl.rgba, Gl.unsignedByte, pixels);

      byte[] lines = new byte[numBytes];

      final int numBytesPerLine = buffer.getWidth()*4;
      for(int i = 0; i < buffer.getHeight(); i++){
        pixels.position((buffer.getHeight() - i - 1) * numBytesPerLine);
        pixels.get(lines, i * numBytesPerLine, numBytesPerLine);
      }

      Pixmap fullPixmap = new Pixmap(buffer.getWidth(), buffer.getHeight());
      Buffers.copy(lines, 0, fullPixmap.pixels, lines.length);

      return new TextureRegion(new Texture(fullPixmap));
    }

    public FrameBuffer toBuffer(FrameBuffer buff, float boundX, float boundY, float scl){
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

      int imageWidth = (int) (width*scl);
      int imageHeight = (int) (height*scl);

      buff.resize(imageWidth, imageHeight);
      buff.begin(Color.clear);
      Draw.proj(camera);
      container.draw();
      Draw.flush();
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

      return buff;
    }

    public void read(Reads read) throws IOException {
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

      int head = read.i();
      if (head != FI_HEAD)
        throw new IOException("file format error, unknown file head: " + Integer.toHexString(head));

      int ver = read.i();

      int cardsLen = read.i();
      for (int i = 0; i < cardsLen; i++) {
        Card card = Card.read(read, ver);
        addCard(card, false);
        card.build();
        card.mul = read.i();
        card.setBounds(read.f(), read.f(), read.f(), read.f());

        int inputs = read.i();
        int outputs = read.i();

        for (int l = 0; l < inputs; l++) {
          ItemLinker linker = readLinker(read, ver);
          linkerMap.put(linker.id, linker);
          card.addIn(linker);
        }

        for (int l = 0; l < outputs; l++) {
          ItemLinker linker = readLinker(read, ver);
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

    private ItemLinker readLinker(Reads read, int ver) {
      long id = read.l();
      ItemLinker res = new ItemLinker(TooManyItems.itemsManager.getByName(read.str()), read.bool(), id);
      res.dir = read.i();
      res.expectAmount = read.f();
      res.setBounds(read.f(), read.f(), read.f(), read.f());
      return res;
    }

    public void write(Writes write){
      write.i(FI_HEAD);
      write.i(0);

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
      write.f(linker.expectAmount);

      write.f(linker.x);
      write.f(linker.y);
      write.f(linker.getWidth());
      write.f(linker.getHeight());
    }
  }

  private void buildItems(Table items, Seq<RecipeItem<?>> list, Cons<RecipeItem<?>> callBack) {
    int[] i = {0};
    boolean[] reverse = {false};
    String[] search = new String[]{""};
    Runnable[] rebuild = new Runnable[1];
    items.table(top -> {
      top.image(Icon.zoom).size(32);

      top.field("", str -> {
        search[0] = str;
        rebuild[0].run();
      }).growX();

      top.button(Icon.none, Styles.clearNonei, 36, () -> {
        i[0] = (i[0] + 1)%TooManyItems.recipesDialog.sortings.size;
        rebuild[0].run();
      }).margin(2).update(b -> b.getStyle().imageUp = TooManyItems.recipesDialog.sortings.get(i[0]).icon);

      top.button(Icon.none, Styles.clearNonei, 36, () -> {
        reverse[0] = !reverse[0];
        rebuild[0].run();
      }).margin(2).update(b -> b.getStyle().imageUp = reverse[0]? Icon.up: Icon.down);
    }).growX();
    items.row();
    items.pane(Styles.smallPane, cont -> {
      rebuild[0] = () -> {
        cont.clearChildren();
        int ind = 0;

        Comparator<RecipeItem<?>> sorting = TooManyItems.recipesDialog.sortings.get(i[0]).sort;
        Seq<RecipeItem<?>> ls = list.copy()
            .removeAll(e -> !e.name().contains(search[0]) && !e.localizedName().contains(search[0]))
            .sort(reverse[0]? (a, b) -> sorting.compare(b, a): sorting);

        for (RecipeItem<?> item : ls) {
          if (item.locked() || (item.item instanceof Item checkVisible && state.rules.hiddenBuildItems.contains(checkVisible)) || item.hidden())
            continue;

          cont.button(new TextureRegionDrawable(item.icon()), Styles.clearNonei, 32, () -> {
            callBack.get(item);
          }).margin(4).tooltip(item.localizedName()).get();

          if (ind++%8 == 7) {
            cont.row();
          }
        }
      };
      rebuild[0].run();
    }).padTop(6).padBottom(4).height(400).fillX();
  }

  private void setMoveLocker(Element inner) {
    inner.addCaptureListener(new InputListener(){
      @Override
      public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
        moveLock(true);
        return true;
      }

      @Override
      public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
        moveLock(false);
      }
    });
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
    public float scale = 1f;

    public static Card read(Reads read, int ver) {
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

    Runnable rebuildConfig, rebuildOptionals, rebuildAttrs;

    float efficiency, multiplier;

    Table over;

    final Recipe recipe;
    final RecipeView recipeView;

    final EnvParameter environments = new EnvParameter();
    final OrderedSet<RecipeItem<?>> optionalSelected = new OrderedSet<>();

    private final EnvParameter param = new EnvParameter();

    public RecipeCard(Recipe recipe) {
      this.recipe = recipe;
      this.recipeView = new RecipeView(recipe, (i, t, m) -> {
        TooManyItems.recipesDialog.toggle = r -> {
          TooManyItems.recipesDialog.hide();

          RecipeCard card = addRecipe(r);

          if (Core.input.keyDown(TooManyItems.binds.hotKey)){
            if (t == NodeType.material){
              ItemLinker linker = card.out.find(e -> e.item == i.item());
              ItemLinker other = in.find(e -> e.item == i.item());
              if (other == null){
                other = new ItemLinker(i.item(), true);
                other.pack();
                addIn(other);

                ItemLinker fo = other;
                Core.app.post(() -> {
                  fo.adsorption(getWidth()/2, 10, this);
                });
              }

              linker.linkTo(other);
            }
            else if (t == NodeType.production){
              ItemLinker linker = out.find(e -> e.item == i.item());
              ItemLinker other = card.in.find(e -> e.item == i.item());

              if (other == null){
                other = new ItemLinker(i.item(), true);
                other.pack();
                card.addIn(other);

                ItemLinker fo = other;
                Core.app.post(() -> {
                  fo.adsorption(card.getWidth()/2, 10, card);
                });
              }
              linker.linkTo(other);
            }
          }
        };
        TooManyItems.recipesDialog.show();
        TooManyItems.recipesDialog.setCurrSelecting(i.item(), m);
      });
      this.recipeView.validate();
    }

    @Override
    public void act(float delta) {
      super.act(delta);

      for (ItemLinker linker : in) {
        RecipeItemStack stack = recipe.materials.get(linker.item);
        if (stack == null) continue;

        if (stack.isBooster) linker.expectAmount = stack.amount*mul*scale;
        else linker.expectAmount = stack.amount*mul*multiplier;
      }

      for (ItemLinker linker : out) {
        RecipeItemStack stack = recipe.productions.get(linker.item);
        if (stack == null) continue;

        linker.expectAmount = stack.amount*mul*efficiency;
      }
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
          setMoveLocker(inner);

          inner.table(inf -> {
            inf.left().add("").growX().update(l -> l.setText(Core.bundle.format("dialog.calculator.recipeMulti", mul))).left().pad(6).padLeft(12).align(left);
            inf.add(Core.bundle.format("dialog.calculator.config")).padLeft(30);
            inf.button(Icon.pencil, Styles.clearNonei, 32, () -> over.visible = true).margin(4);
            inf.row();
            inf.left().add("").colspan(3).growX().update(l -> l.setText(Core.bundle.format("dialog.calculator.recipeEff", (efficiency == 1? "": efficiency > 1? "[#98ffa9]": "[red]") + Strings.autoFixed(efficiency*100, 1)))).left().pad(6).padLeft(12).align(left);
          }).growX();

          inner.row();
          inner.table(i -> i.add(recipeView).fill()).center().fill().pad(36).padTop(12);

          inner.fill(over -> {
            this.over = over;
            over.visible = false;
            over.table(Consts.darkGrayUIAlpha, table -> {
              rebuildConfig = () -> {
                calculateEfficiency();
                table.clearChildren();

                table.top();
                table.table(Consts.grayUI, b -> {
                  b.table(Consts.darkGrayUIAlpha, pane -> {
                    pane.add(Core.bundle.get("dialog.calculator.config")).growX().padLeft(10);
                    pane.button(Icon.cancel, Styles.clearNonei, 32, () -> over.visible = false).margin(4);
                  }).growX();
                }).growX();
                table.row();
                table.table(r -> {
                  r.left().defaults().left().padBottom(4);
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

                  r.add(Core.bundle.get("calculator.config.efficiencyScl"));
                  r.table(inp -> {
                    inp.field(Strings.autoFixed(scale*100, 1), TextField.TextFieldFilter.floatsOnly, i -> {
                      try {
                        scale = i.isEmpty()? 0: Float.parseFloat(i)/100;
                        calculateEfficiency();
                      } catch (Throwable ignored){}
                    }).growX().get().setAlignment(right);
                    inp.add("%").color(Color.gray);
                  }).growX().padLeft(20);
                  r.row();

                  r.add(Core.bundle.get("calculator.config.optionalMats"));
                  r.table(in -> in.right().button(Icon.settingsSmall, Styles.clearNonei, 24, () -> {
                    showMenu(menu -> {
                      menu.table(Consts.darkGrayUIAlpha, i -> {
                        i.update(() -> {
                          if (Core.input.keyDown(KeyCode.mouseLeft) || Core.input.keyDown(KeyCode.mouseRight)){
                            tmp.set(Core.input.mouse());
                            i.stageToLocalCoordinates(tmp);

                            if (tmp.x > i.getWidth() || tmp.y > i.getHeight() || tmp.x < 0 || tmp.y < 0){
                              hideMenu();
                            }
                          }
                        });

                        i.add(Core.bundle.get("calculator.config.selectOptionals")).color(Pal.accent).pad(8).growX().left();
                        i.row();
                        if (!recipe.materials.values().toSeq().contains(e -> e.optionalCons && !e.isAttribute)){
                          i.add(Core.bundle.get("calculator.config.noOptionals")).color(Color.lightGray).pad(8).growX().left();
                        }
                        else {
                          i.pane(p -> {
                            for (RecipeItemStack stack : recipe.materials.values()) {
                              if (!stack.optionalCons || stack.isAttribute) continue;
                              CheckBox item = Elem.newCheck("", b -> {
                                if (b) optionalSelected.add(stack.item);
                                else optionalSelected.remove(stack.item);

                                calculateEfficiency();
                                rebuildOptionals.run();
                              });
                              item.setChecked(optionalSelected.contains(stack.item));
                              item.image(stack.item.icon()).size(36).scaling(Scaling.fit);
                              item.add(stack.item.localizedName()).padLeft(5).growX().left();
                              item.table(am -> {
                                am.left().bottom();
                                am.add(stack.getAmount(), Styles.outlineLabel);
                                am.pack();
                              }).padLeft(5).fill().left();

                              p.add(item).margin(6).growX();
                              p.row();
                            }
                          }).grow();
                        }
                      }).grow().maxHeight(400).minWidth(260);
                    }, in, topRight, topLeft, true);
                  }).right().fill().margin(4)).growX();
                  r.row();
                  r.pane(mats -> {
                    rebuildOptionals = () -> {
                      mats.clearChildren();
                      mats.left().top().defaults().left();
                      if (optionalSelected.isEmpty()) {
                        mats.add(Core.bundle.get("misc.empty"), Styles.outlineLabel).pad(6).color(Color.gray);
                      } else {
                        for (RecipeItem<?> item : optionalSelected) {
                          mats.table(i -> {
                            i.left().defaults().left();
                            i.image(item.icon()).size(32).scaling(Scaling.fit);
                            i.add(item.localizedName()).padLeft(4);
                          }).growX().margin(6);
                          mats.add("").growX().padLeft(4).update(l -> {
                            RecipeItemStack stack = recipe.materials.get(item);
                            float am = stack.amount*mul*(stack.isBooster? scale: multiplier)*60;
                            l.setText((am > 1000? UI.formatAmount((long) am): Strings.autoFixed(am, 1)) + "/s");
                          }).labelAlign(right);
                          mats.row();
                        }
                      }
                    };
                    rebuildOptionals.run();
                  }).colspan(2).fillY().minHeight(40).growX().scrollX(false).left();

                  r.row();
                  r.add(Core.bundle.get("calculator.config.attributes"));
                  r.table(in -> in.right().button(Icon.settingsSmall, Styles.clearNonei, 24, () -> {
                    showMenu(menu -> {
                      menu.table(Consts.darkGrayUIAlpha, i -> {
                        i.update(() -> {
                          if (Core.input.keyDown(KeyCode.mouseLeft) || Core.input.keyDown(KeyCode.mouseRight)){
                            tmp.set(Core.input.mouse());
                            i.stageToLocalCoordinates(tmp);

                            if (tmp.x > i.getWidth() || tmp.y > i.getHeight() || tmp.x < 0 || tmp.y < 0){
                              hideMenu();
                            }
                          }
                        });

                        i.add(Core.bundle.get("calculator.config.selectAttributes")).color(Pal.accent).pad(8).padBottom(4).growX().left();
                        i.row();
                        if (!recipe.materials.values().toSeq().contains(e -> e.isAttribute)){
                          i.add(Core.bundle.get("calculator.config.noAttributes")).color(Color.lightGray).pad(8).growX().left();
                        }
                        else {
                          i.add(Core.bundle.get("calculator.config.attrTip")).color(Color.lightGray).pad(8).padTop(4).growX().left();
                          i.row();
                          i.pane(p -> {
                            for (RecipeItemStack stack : recipe.materials.values()) {
                              if (!stack.isAttribute) continue;
                              p.table(item -> {
                                item.image(stack.item.icon()).size(36).scaling(Scaling.fit);
                                item.add(stack.item.localizedName()).padLeft(5).growX().left();
                                item.table(am -> {
                                  am.left().bottom();
                                  am.add(stack.getAmount(), Styles.outlineLabel);
                                  am.pack();
                                }).padLeft(5).fill().left();

                                TextField field = item.field((int)environments.getAttribute(stack.item) + "", TextField.TextFieldFilter.digitsOnly, f -> {
                                  environments.resetAttr(stack.item);
                                  int amount = Strings.parseInt(f, 0);
                                  if (amount > 0) environments.add(stack.item, amount, true);

                                  calculateEfficiency();
                                  rebuildAttrs.run();
                                }).get();
                                field.setProgrammaticChangeEvents(true);

                                item.check("", b -> {
                                  if (b) field.setText(((int) stack.amount) + "");
                                  else field.setText("0");
                                }).update(c -> c.setChecked(environments.getAttribute(stack.item) >= stack.amount));
                              }).margin(6).growX();

                              p.row();
                            }
                          }).grow();
                        }
                      }).grow().maxHeight(400).minWidth(260);
                    }, in, topRight, topLeft, true);
                  }).right().fill().margin(4)).growX();
                  r.row();
                  r.pane(attr -> {
                    rebuildAttrs = () -> {
                      attr.clearChildren();
                      attr.left().top().defaults().left();
                      if (!environments.hasAttrs()) {
                        attr.add(Core.bundle.get("misc.empty"), Styles.outlineLabel).pad(6).color(Color.gray);
                      } else {
                        environments.eachAttribute((item, f) -> {
                          attr.table(i -> {
                            i.left().defaults().left();
                            i.image(item.icon()).size(32).scaling(Scaling.fit);
                            i.add(item.localizedName()).padLeft(4);
                            i.add("x" + f.intValue(), Styles.outlineLabel).pad(6).color(Color.lightGray);
                          }).fill().margin(6);
                          attr.row();
                        });
                      }
                    };
                    rebuildAttrs.run();
                  }).colspan(2).minHeight(40).fillY().growX().scrollX(false).left();
                }).margin(10).fillY().growX().left();
              };

              rebuildConfig.run();
            }).grow();
          });
        });
      }).fill();
    }

    public void calculateEfficiency(){
      param.clear();
      for (ObjectMap.Entry<RecipeItem<?>, RecipeItemStack> entry : recipe.materials) {
        if (entry.value.optionalCons && entry.value.isBooster && !entry.value.isAttribute && optionalSelected.contains(entry.key)) param.add(entry.key, entry.value.amount, false);
      }
      multiplier = recipe.calculateMultiple(param.setAttributes(environments))*scale;

      param.applyFullRecipe(recipe, false, false, multiplier);

      efficiency = recipe.calculateEfficiency(param, multiplier);
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
    public void act(float delta) {
      super.act(delta);

      if (isInput && !out.isEmpty()) out.first().expectAmount = stack.amount;
      else {
        for (ItemLinker linker : in) {
          linker.expectAmount = stack.amount;
        }
      }
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

            setMoveLocker(ta);

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
    public float expectAmount = 0;

    public final boolean isInput;

    OrderedMap<ItemLinker, float[]> links = new OrderedMap<>();
    ObjectMap<ItemLinker, Seq<Line>> lines = new ObjectMap<>();
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
            inc.add("", Styles.outlineLabel).padTop(20).update(l -> l.setText(expectAmount <= 0? "--/s":
                (expectAmount*60 > 1000? UI.formatAmount((long) (expectAmount*60)): Strings.autoFixed(expectAmount*60, 1)) + "/s\n"
            )).get().setAlignment(center);
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

          if (!panned && view.selecting == null) {
            view.selecting = ItemLinker.this;
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

        ItemLinker linker = card.in.find(l -> l.item == item);

        if (linker == null) linker = card.hitLinker(hovering.x, hovering.y);
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

      float angle = Angles.angle(targetCard.child.x + targetCard.child.getWidth()/2, targetCard.child.y + targetCard.child.getHeight()/2, posX, posY);
      float check = Angles.angle(targetCard.getWidth(), targetCard.getHeight());

      if (angle > check && angle < 180 - check) {
        dir = 1;
        float offY = targetCard.child.getHeight()/2 + getHeight()/1.5f;
        setPosition(posX, targetCard.child.y + targetCard.child.getHeight()/2 + offY, Align.center);
      } else if (angle > 180 - check && angle < 180 + check) {
        dir = 2;
        float offX = -targetCard.child.getWidth()/2 - getWidth()/1.5f;
        setPosition(targetCard.child.x + targetCard.child.getWidth()/2 + offX, posY, Align.center);
      } else if (angle > 180 + check && angle < 360 - check) {
        dir = 3;
        float offY = -targetCard.child.getHeight()/2 - getHeight()/1.5f;
        setPosition(posX, targetCard.child.y + targetCard.child.getHeight()/2 + offY, Align.center);
      } else {
        dir = 0;
        float offX = targetCard.child.getWidth()/2 + getWidth()/1.5f;
        setPosition(targetCard.child.x + targetCard.child.getWidth()/2 + offX, posY, Align.center);
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

      Seq<Line> line = new Seq<>();
      links.put(target, new float[]{-1, 0});
      target.links.put(this, new float[]{-1, 0});
      lines.put(target, line);
      target.lines.put(this, line);

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
      lines.remove(target);
      target.lines.remove(this);
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
      res.expectAmount = expectAmount;
      res.dir = dir;

      return res;
    }

    public boolean isNormalized() {
      if (links.size == 1) return true;

      float total = 0;
      for (float[] value : links.values()) {
        if (value[0] < 0) return false;
        total += value[0];

        if (total > 1 + Mathf.FLOAT_ROUNDING_ERROR) return false;
      }

      return Mathf.equal(total, 1);
    }
  }

  protected static class Line {
    //TODO: 链接路线数据
  }
}
