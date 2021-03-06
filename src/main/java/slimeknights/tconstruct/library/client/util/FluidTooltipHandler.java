package slimeknights.tconstruct.library.client.util;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.Item;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.RecipeManager;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ModList;
import slimeknights.mantle.recipe.FluidIngredient;
import slimeknights.mantle.recipe.RecipeHelper;
import slimeknights.tconstruct.library.Util;
import slimeknights.tconstruct.library.materials.MaterialValues;
import slimeknights.tconstruct.library.recipe.RecipeTypes;
import slimeknights.tconstruct.library.recipe.casting.AbstractCastingRecipe;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FluidTooltipHandler {
  private static final Map<Fluid,List<FluidGuiEntry>> CACHE = new HashMap<>();
  public static final ITextComponent HOLD_SHIFT = new TranslationTextComponent(Util.makeTranslationKey("gui", "fluid.hold_shift")).mergeStyle(TextFormatting.GRAY);
  public static final String PER_SECOND = Util.makeTranslationKey("gui", "fluid.per_second");

  /*
   * Base units
   */
  private static final FluidGuiEntry KILOBUCKET = new FluidGuiEntry("kilobucket", 1000000);
  private static final FluidGuiEntry BUCKET = new FluidGuiEntry("bucket", 1000);
  private static final FluidGuiEntry MILLIBUCKET = new FluidGuiEntry("millibucket", 1);
  private static final FluidGuiEntry INGOT = new FluidGuiEntry("ingot", MaterialValues.VALUE_Ingot);
  private static final FluidGuiEntry BLOCK = new FluidGuiEntry("block", MaterialValues.VALUE_Block);

  /** List of options to check for table cast recipes */
  private static final Map<Item,FluidGuiEntry> TOOLTIP_OPTIONS = new IdentityHashMap<>();
  /** List of options to check for table with no cast recipes */
  private static final Map<Integer,FluidGuiEntry> TABLE_TOP_OPTIONS = new HashMap<>();

  /** Initializes the tooltip handler */
  public static void init() {
    MinecraftForge.EVENT_BUS.addListener(FluidTooltipHandler::onRecipesUpdated);
    TOOLTIP_OPTIONS.put(TinkerSmeltery.ingotCast.get(), INGOT);
    TOOLTIP_OPTIONS.put(TinkerSmeltery.nuggetCast.get(), new FluidGuiEntry("nugget", MaterialValues.VALUE_Nugget));
    TOOLTIP_OPTIONS.put(TinkerSmeltery.gemCast.get(), new FluidGuiEntry("gem", MaterialValues.VALUE_Gem));
    for (FluidGuiEntry entry : new FluidGuiEntry[] {
      new FluidGuiEntry("pane", MaterialValues.VALUE_Pane),
      new FluidGuiEntry("slimeball", MaterialValues.VALUE_SlimeBall)
    }) {
      TABLE_TOP_OPTIONS.put(entry.needed, entry);
    }
  }

  /**
   * Called when recipes are synced from the server to the client
   * @param event  Event instance
   */
  @SuppressWarnings("unused")
  private static void onRecipesUpdated(RecipesUpdatedEvent event) {
    CACHE.clear();
  }

  /**
   * Gets the tooltip for a fluid stack
   * @param fluid  Fluid stack instance
   * @return  Fluid tooltip
   */
  public static List<ITextComponent> getFluidTooltip(FluidStack fluid) {
    return getFluidTooltip(fluid, fluid.getAmount());
  }

  /**
   * Gets the tooltip for a fluid stack
   * @param fluid  Fluid stack instance
   * @param amount Amount override
   * @return  Fluid tooltip
   */
  public static List<ITextComponent> getFluidTooltip(FluidStack fluid, int amount) {
    List<ITextComponent> tooltip = new ArrayList<>();
    // fluid name, not sure if there is a cleaner way to do this
    tooltip.add(fluid.getDisplayName().copyRaw().mergeStyle(TextFormatting.WHITE));
    // material
    appendMaterial(fluid.getFluid(), amount, false, tooltip);
    // add mod display name
    ModList.get().getModContainerById(Objects.requireNonNull(fluid.getFluid().getRegistryName()).getNamespace())
           .map(container -> container.getModInfo().getDisplayName())
           .ifPresent(name -> tooltip.add(new StringTextComponent(name).mergeStyle(TextFormatting.BLUE, TextFormatting.ITALIC)));
    return tooltip;
  }

  /**
   * Adds information for the tooltip based on material units
   * @param fluid    Input fluid stack
   * @param tooltip  Tooltip to append information
   */
  public static void appendMaterial(FluidStack fluid, List<ITextComponent> tooltip) {
    appendMaterial(fluid.getFluid(), fluid.getAmount(), false, tooltip);
  }

  /**
   * Adds information for the tooltip based on material units
   * @param fluid      Input fluid
   * @param original   Input amount
   * @param perSecond  If true, formats as a rate per second
   * @param tooltip    Tooltip to append information
   */
  public static void appendMaterial(Fluid fluid, int original, boolean perSecond, List<ITextComponent> tooltip) {
    int amount = original;

    // if holding shift, skip specific units
    if(!Screen.hasShiftDown()) {
      List<FluidGuiEntry> entries = CACHE.computeIfAbsent(fluid, FluidTooltipHandler::calcFluidEntries);
      for(FluidGuiEntry entry : entries) {
        amount = entry.getText(tooltip, perSecond, amount);
      }
    }

    // standard display stuff: bucket amounts
    appendBuckets(amount, perSecond, tooltip);

    // add hold shift message
    if (amount != original) {
      appendShift(tooltip);
    }
  }

  /**
   * Appends the hold shift message to the tooltip
   * @param tooltip  Tooltip to append information
   */
  public static void appendShift(List<ITextComponent> tooltip) {
    if(!Screen.hasShiftDown()) {
      tooltip.add(new StringTextComponent(""));
      tooltip.add(HOLD_SHIFT);
    }
  }

  /**
   * Adds information to the tooltip based on ingot units
   * @param amount   Fluid amount
   * @param tooltip  Tooltip to append information
   */
  public static void appendIngots(int amount, List<ITextComponent> tooltip) {
    amount = INGOT.getText(tooltip, false, amount);
    appendBuckets(amount, tooltip);
  }

  /**
   * Adds information to the tooltip based on the fluid using bucket units
   * @param amount   Fluid amount
   * @param tooltip  Tooltip to append information
   */
  public static void appendBuckets(int amount, List<ITextComponent> tooltip) {
    appendBuckets(amount, false, tooltip);
  }

  /**
   * Adds information to the tooltip based on the fluid using bucket units
   * @param amount     Fluid amount
   * @param perSecond  If true, formats as a rate per second
   * @param tooltip  Tooltip to append information
   */
  public static void appendBuckets(int amount, boolean perSecond, List<ITextComponent> tooltip) {
    amount = KILOBUCKET.getText(tooltip, perSecond, amount);
    amount = BUCKET.getText(tooltip, perSecond, amount);
    MILLIBUCKET.getText(tooltip, perSecond, amount);
  }

  /**
   * Gets all relevant entries for a fluid
   * @param fluid  Relevant fluid
   * @return  List of entries for the fluid
   */
  private static List<FluidGuiEntry> calcFluidEntries(Fluid fluid) {
    assert Minecraft.getInstance().world != null;
    RecipeManager manager = Minecraft.getInstance().world.getRecipeManager();

    // first, search casting recipes for cast items
    List<FluidGuiEntry> list = new ArrayList<>();
    for (AbstractCastingRecipe recipe : RecipeHelper.getRecipes(manager, RecipeTypes.CASTING_TABLE, AbstractCastingRecipe.class)) {
      // if the fluid matches, move onto cast search
      FluidIngredient ingredient = recipe.getFluid();
      if (ingredient.test(fluid)) {
        Ingredient cast = recipe.getCast();
        // if empty, add an entry if a table recipe matches an expected unit
        if (cast == Ingredient.EMPTY) {
          Optional.ofNullable(TABLE_TOP_OPTIONS.get(ingredient.getAmount(fluid))).ifPresent(list::add);
        } else {
          // if a cast, check for a matching item in the map
          Arrays.stream(recipe.getCast().getMatchingStacks())
                .map(stack -> TOOLTIP_OPTIONS.get(stack.getItem()))
                .filter(Objects::nonNull)
                .findFirst()
                .ifPresent(entry -> list.add(entry.withAmount(ingredient.getAmount(fluid))));
        }
      }
    }

    // next, iterate basin recipes to find block amounts
    for (AbstractCastingRecipe recipe : RecipeHelper.getRecipes(manager, RecipeTypes.CASTING_BASIN, AbstractCastingRecipe.class)) {
      // no cast, copy amount
      FluidIngredient ingredient = recipe.getFluid();
      if (recipe.getCast() == Ingredient.EMPTY && ingredient.test(fluid)) {
        list.add(BLOCK.withAmount(ingredient.getAmount(fluid)));
      }
    }

    // important that the largest value is first, as that is how the entries are processed
    list.sort(Collections.reverseOrder(Comparator.comparingInt(FluidGuiEntry::getNeeded)));
    return list;
  }

  private static class FluidGuiEntry {
    private final String translationKey;
    @Getter
    private final int needed;

    /**
     * Creates a new fluid GUI entry
     * @param name    Base translation name
     * @param needed  Amount needed
     */
    private FluidGuiEntry(String name, int needed) {
      this.translationKey = Util.makeTranslationKey("gui", "fluid." + name);
      this.needed = needed;
    }

    /**
     * Copies an entry into another amount
     * @param parent  Parent entry
     * @param needed  New needed amount
     */
    private FluidGuiEntry(FluidGuiEntry parent, int needed) {
      this.translationKey = parent.translationKey;
      this.needed = needed;
    }

    /**
     * Gets an entry with the given amount
     * @param amount  Amount
     * @return  this if amount matches, new entry if no match
     */
    private FluidGuiEntry withAmount(int amount) {
      if (amount == this.needed) {
        return this;
      }
      return new FluidGuiEntry(this, amount);
    }

    /**
     * Gets the display text for this fluid entry
     * @return  Display text
     */
    private int getText(List<ITextComponent> tooltip, boolean perSecond, int amount) {
      int full = amount / needed;
      if (full > 0) {
        IFormattableTextComponent component = new TranslationTextComponent(translationKey, full);
        if (perSecond) {
          component = new TranslationTextComponent(PER_SECOND, component);
        }
        tooltip.add(component.mergeStyle(TextFormatting.GRAY));
      }
      return amount % needed;
    }
  }
}
