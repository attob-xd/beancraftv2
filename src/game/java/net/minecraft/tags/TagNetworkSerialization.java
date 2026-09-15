package net.minecraft.tags;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public class TagNetworkSerialization {
   public static Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> serializeTagsToNetwork(RegistryAccess var0) {
      return var0.networkSafeRegistries()
         .map(var0x -> Pair.of(var0x.key(), serializeToNetwork(var0x.value())))
         .filter(var0x -> !((TagNetworkSerialization.NetworkPayload)var0x.getSecond()).isEmpty())
         .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
   }

   private static <T> TagNetworkSerialization.NetworkPayload serializeToNetwork(Registry<T> var0) {
      HashMap var1 = new HashMap();
      var0.getTags().forEach(var2 -> {
         // Parameterised against the decompiler's raw types: iterating a raw HolderSet yields
         // Object, so Holder.value() did not type-check here.
         HolderSet<T> var3 = (HolderSet<T>)var2.getSecond();
         IntArrayList var4 = new IntArrayList(var3.size());

         for (Holder<T> var6 : var3) {
            if (var6.kind() != Holder.Kind.REFERENCE) {
               throw new IllegalStateException("Can't serialize unregistered value " + var6);
            }

            var4.add(var0.getId(var6.value()));
         }

         var1.put(((TagKey)var2.getFirst()).location(), var4);
      });
      return new TagNetworkSerialization.NetworkPayload(var1);
   }

   public static <T> void deserializeTagsFromNetwork(
      ResourceKey<? extends Registry<T>> var0, Registry<T> var1, TagNetworkSerialization.NetworkPayload var2, TagNetworkSerialization.TagOutput<T> var3
   ) {
      var2.tags.forEach((var3x, var4) -> {
         TagKey var5 = TagKey.create(var0, var3x);
         // Vanilla is:
         //
         //     List var6 = var4.intStream().mapToObj(var1::getHolder).flatMap(Optional::stream).toList();
         //
         // fastutil's IntCollection.intStream() is a default method that calls
         // StreamSupport.intStream(spliterator(), false), and TeaVM has no spliterator-backed
         // IntStream - its only int streams come from an array or from mapping another stream.
         // So this threw NoSuchMethodError on
         // StreamSupport.intStream(Ljava/util/Spliterator$OfInt;Z)Ljava/util/stream/IntStream;
         // and took ClientboundUpdateTagsPacket with it, meaning the client joined a server
         // with no tags at all.
         //
         // The loop is what the stream chain says, without the stream: for each registry id,
         // take the holder if the registry has one and drop it otherwise. Same order, same
         // filtering. This is the same substitution EntitySectionStorage needed for LongStream,
         // and it avoids boxing every id on the way through.
         List<Holder<T>> var6 = new ArrayList<>(var4.size());
         for (int var7 = 0, var8 = var4.size(); var7 < var8; ++var7) {
            Optional<Holder<T>> var9 = var1.getHolder(var4.getInt(var7));
            if (var9.isPresent()) {
               var6.add(var9.get());
            }
         }
         var3.accept(var5, var6);
      });
   }

   public static final class NetworkPayload {
      final Map<ResourceLocation, IntList> tags;

      NetworkPayload(Map<ResourceLocation, IntList> var1) {
         this.tags = var1;
      }

      public void write(FriendlyByteBuf var1) {
         var1.writeMap(this.tags, FriendlyByteBuf::writeResourceLocation, FriendlyByteBuf::writeIntIdList);
      }

      public static TagNetworkSerialization.NetworkPayload read(FriendlyByteBuf var0) {
         return new TagNetworkSerialization.NetworkPayload(var0.readMap(FriendlyByteBuf::readResourceLocation, FriendlyByteBuf::readIntIdList));
      }

      public boolean isEmpty() {
         return this.tags.isEmpty();
      }
   }

   @FunctionalInterface
   public interface TagOutput<T> {
      void accept(TagKey<T> var1, List<Holder<T>> var2);
   }
}
