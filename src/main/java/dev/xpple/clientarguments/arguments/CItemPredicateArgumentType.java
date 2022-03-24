package dev.xpple.clientarguments.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.command.argument.ItemStringReader;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.tag.TagKey;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.registry.Registry;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public class CItemPredicateArgumentType implements ArgumentType<CItemPredicateArgumentType.ItemPredicateArgument> {
	private static final Collection<String> EXAMPLES = Arrays.asList("stick", "minecraft:stick", "#stick", "#stick{foo=bar}");
	private static final DynamicCommandExceptionType UNKNOWN_TAG_EXCEPTION = new DynamicCommandExceptionType(id -> new TranslatableText("carguments.item.tag.unknown", id));

	public static CItemPredicateArgumentType itemPredicate() {
		return new CItemPredicateArgumentType();
	}

	@Override
	public ItemPredicateArgument parse(final StringReader stringReader) throws CommandSyntaxException {
		ItemStringReader itemStringReader = (new ItemStringReader(stringReader, true)).consume();
		if (itemStringReader.getItem() != null) {
			ItemPredicate itemPredicate = new ItemPredicate(itemStringReader.getItem(), itemStringReader.getNbt());
			return context -> itemPredicate;
		}
		TagKey<Item> itemPredicate = itemStringReader.getId();
		return commandContext -> {
			if (!Registry.ITEM.containsTag(itemPredicate)) {
				throw UNKNOWN_TAG_EXCEPTION.create(itemPredicate);
			}
			return new TagPredicate(itemPredicate, itemStringReader.getNbt());
		};
	}

	public static Predicate<ItemStack> getCItemPredicate(CommandContext<FabricClientCommandSource> context, String name) throws CommandSyntaxException {
		return context.getArgument(name, ItemPredicateArgument.class).create(context);
	}

	@Override
	public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
		StringReader stringReader = new StringReader(builder.getInput());
		stringReader.setCursor(builder.getStart());
		ItemStringReader itemStringReader = new ItemStringReader(stringReader, true);
		try {
			itemStringReader.consume();
		} catch (CommandSyntaxException ignored) {
		}
		return itemStringReader.getSuggestions(builder, Registry.ITEM);
	}

	@Override
	public Collection<String> getExamples() {
		return EXAMPLES;
	}

	private static class ItemPredicate implements Predicate<ItemStack> {
		private final Item item;
		@Nullable
		private final NbtCompound nbt;

		public ItemPredicate(Item item, @Nullable NbtCompound nbt) {
			this.item = item;
			this.nbt = nbt;
		}

		@Override
		public boolean test(ItemStack itemStack) {
			return itemStack.isOf(this.item) && NbtHelper.matches(this.nbt, itemStack.getNbt(), true);
		}
	}

	public interface ItemPredicateArgument {
		Predicate<ItemStack> create(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException;
	}

	private static class TagPredicate implements Predicate<ItemStack> {
		private final TagKey<Item> tag;
		@Nullable
		private final NbtCompound compound;

		public TagPredicate(TagKey<Item> tag, @Nullable NbtCompound nbt) {
			this.tag = tag;
			this.compound = nbt;
		}

		@Override
		public boolean test(ItemStack itemStack) {
			return itemStack.isIn(this.tag) && NbtHelper.matches(this.compound, itemStack.getNbt(), true);
		}

		public String getPrettyString() {
			String ret = "#" + this.tag.id();
			if (this.compound != null) {
				ret += this.compound;
			}
			return ret;
		}
	}
}
