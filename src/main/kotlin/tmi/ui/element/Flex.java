package tmi.ui.element;

import arc.func.Cons;
import arc.scene.Element;
import arc.scene.ui.layout.WidgetGroup;
import arc.struct.SnapshotSeq;

public class Flex extends WidgetGroup {
  private boolean sizeInvalidate;
  private boolean wrap = true;

  private float prefWidth;
  private float prefHeight;

  public Flex(){}

  public Flex(Cons<Flex> build){
    build.get(this);
  }

  public void setWrap(boolean wrap){
    if (wrap != this.wrap) {
      this.wrap = wrap;
      invalidate();
    }
  }

  public boolean getWrap(){
    return wrap;
  }

  public void calculateSize(){
    sizeInvalidate = false;

    if (wrap) {
      float targetWidth = getWidth();

      float width = 0;
      float height = 0;

      float maxHeight = 0;

      SnapshotSeq<Element> seq = getChildren();
      Element[] list = seq.begin();
      for (int i = 0, n = seq.size; i < n; i++) {
        Element e = list[i];

        float w = e.getWidth();
        float h = e.getHeight();
        if (width + w > targetWidth) {
          height += maxHeight;
          maxHeight = 0;
          width = 0;
        }
        width += w;
        maxHeight = Math.max(maxHeight, h);
      }
      height += maxHeight;
      seq.end();

      prefWidth = targetWidth;
      prefHeight = height;
    }
    else {
      float width = 0;

      float maxHeight = 0;

      SnapshotSeq<Element> seq = getChildren();
      Element[] list = seq.begin();
      for (int i = 0, n = seq.size; i < n; i++) {
        Element e = list[i];

        float w = e.getWidth();
        float h = e.getHeight();

        width += w;
        maxHeight = Math.max(maxHeight, h);
      }
      seq.end();

      prefWidth = width;
      prefHeight = maxHeight;
    }
  }

  @Override
  public void invalidate() {
    super.invalidate();
    sizeInvalidate = true;
  }

  @Override
  public float getPrefWidth() {
    if (sizeInvalidate) calculateSize();

    return prefWidth;
  }

  @Override
  public float getPrefHeight() {
    if (sizeInvalidate) calculateSize();

    return prefHeight;
  }
}
