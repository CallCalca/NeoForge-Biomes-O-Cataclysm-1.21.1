package net.calca.biomesofcataclysms.data;

import net.calca.biomesofcataclysms.data.chunk.ChunkInstance;
import net.calca.biomesofcataclysms.manager.ChunkProcessorManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

public class DataSavingHelper{
        private static String dimToString(ResourceKey<Level> dim) {
            return dim.location().toString();
        }

        public static ResourceKey<Level> stringToDim(String s) {
            return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(s));
        }

        public static void writeLongDequeMap(CompoundTag root, String name,
                                             Map<ResourceKey<Level>, ArrayDeque<Long>> map) {
            CompoundTag section = new CompoundTag();

            Map<ResourceKey<Level>, ArrayDeque<Long>> copy = new HashMap<>();
            for (var entry : map.entrySet()) {
                copy.put(entry.getKey(), new ArrayDeque<>(entry.getValue()));
            }

            for (var entry : copy.entrySet()) {
                long[] values = entry.getValue().stream().mapToLong(Long::longValue).toArray();
                section.putLongArray(dimToString(entry.getKey()), values);
            }

            root.put(name, section);
        }

        public static void readLongDequeMap(CompoundTag root, String name,
                                            Map<ResourceKey<Level>, ArrayDeque<Long>> map) {
            map.clear();
            if (!root.contains(name, Tag.TAG_COMPOUND)) return;

            CompoundTag section = root.getCompound(name);
            for (String dimId : section.getAllKeys()) {
                long[] values = section.getLongArray(dimId);
                ArrayDeque<Long> deque = new ArrayDeque<>(values.length);
                for (long value : values) {
                    deque.addLast(value);
                }
                map.put(stringToDim(dimId), deque);
            }
        }

        public static void writeStateMap(CompoundTag root, String name,
                                         Map<ResourceKey<Level>, ChunkProcessorManager.DimensionState> map) {
            CompoundTag section = new CompoundTag();

            Map<ResourceKey<Level>, ChunkProcessorManager.DimensionState> copy = new HashMap<>();
            for (var entry : map.entrySet()) {
                ChunkProcessorManager.DimensionState original = entry.getValue();
                ChunkProcessorManager.DimensionState state = new ChunkProcessorManager.DimensionState();
                state.currentKey = original.currentKey;
                state.step = original.step;
                copy.put(entry.getKey(), state);
            }

            for (var entry : copy.entrySet()) {
                CompoundTag stateTag = new CompoundTag();
                ChunkProcessorManager.DimensionState state = entry.getValue();

                stateTag.putBoolean("hasCurrentKey", state.currentKey != null);
                if (state.currentKey != null) {
                    stateTag.putLong("currentKey", state.currentKey);
                }
                stateTag.putInt("step", state.step);

                section.put(dimToString(entry.getKey()), stateTag);
            }

            root.put(name, section);
        }

        public static void readStateMap(CompoundTag root, String name,
                                        Map<ResourceKey<Level>, ChunkProcessorManager.DimensionState> map) {
            map.clear();
            if (!root.contains(name, Tag.TAG_COMPOUND)) return;

            CompoundTag section = root.getCompound(name);
            for (String dimId : section.getAllKeys()) {
                CompoundTag stateTag = section.getCompound(dimId);
                ChunkProcessorManager.DimensionState state = new ChunkProcessorManager.DimensionState();

                if (stateTag.getBoolean("hasCurrentKey")) {
                    state.currentKey = stateTag.getLong("currentKey");
                }
                state.step = stateTag.getInt("step");

                map.put(stringToDim(dimId), state);
            }
        }

}
