# TooManyItems Item Manager (TMI)

[点击此处查看中文版](./README.md)

---

This mod aims to provide a convenient interface for quickly querying information about item production and manufacturing, which is very useful when you have many mods installed.

![preview](assets/git/preview.png)

As shown above, you can simply select and query the manufacturing information of the items you want to learn about from the list, including the usage of an item, how to obtain it, the construction cost of a block, and the processing procedures available in factories, etc.

- After installing this mod, a draggable button will appear on your game screen (initially displayed in the bottom-left corner). Click this button to directly open the TMI Item Manager page.  
- Additionally, a `TMI Item Manager` button will be added to the core database, which can also open this page.
- In the detailed information of an item, if the item has recipe attributes, a button will be added to the details page to directly jump to the recipe page for that item.

All buttons look like this:

![button](assets/git/tmibutton.png)

Generally speaking, this mod supports all JSON-based mods and most third-party mods that do not have extensive custom production types. If a third-party mod has processing or manufacturing processes not based on vanilla, it may require adaptation by the author. Please refer to [this project's Wiki](https://github.com/EB-wilson/TooManyItems/wiki).

---

### About Hotkeys and Touch Operations

On the page shown above, each item on the left and right sides can be clicked to open its acquisition method. To view its usage, in keyboard and mouse mode, TMI has a new hotkey bound in the key settings:

![img.png](assets/git/binding.png)

While holding this hotkey, click on an item to query its usage.

If you are using a touch device, quickly double-tap an item to query its usage. Whether using touch or keyboard and mouse, you can long-press an item for one second to open its detailed information (if available). The one-second long-press progress is also displayed as an animation.