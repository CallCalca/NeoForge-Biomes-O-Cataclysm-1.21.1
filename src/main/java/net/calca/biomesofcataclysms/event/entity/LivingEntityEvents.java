package net.calca.biomesofcataclysms.event.entity;


import net.calca.biomesofcataclysms.BiomesOfCataclysms;
import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.data.server.PersistentData;
import net.calca.biomesofcataclysms.cataclysm.AllCataclysms;
import net.calca.biomesofcataclysms.cataclysm.eternaleclipse.EternalEclipseStage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.AnimalTameEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Optional;
import java.util.UUID;

import static net.calca.biomesofcataclysms.event.player.PlayerEvents.*;

@EventBusSubscriber(modid = BiomesOfCataclysms.MODID, bus = EventBusSubscriber.Bus.GAME)
public class LivingEntityEvents {

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        // Lavoriamo solo sul lato Server
        if (event.getLevel().isClientSide()) return;

        // Controlliamo se l'entità che sta spawnando è un Mostro ed è un PathfinderMob (necessario per i Goal)
        if (event.getEntity() instanceof Monster monster && monster instanceof PathfinderMob pathfinderMob) {

            // Priorità 3: Distanza di avvistamento = 16 blocchi, Velocità da camminata = 1.0, Velocità da corsa = 1.2

            // 1. Fuga dai Gatti
            pathfinderMob.goalSelector.addGoal(3, new AvoidEntityGoal<>(
                    pathfinderMob,
                    Cat.class,
                    16.0F, // Raggio entro cui il mostro nota il gatto
                    1.0D,  // Moltiplicatore velocità di allontanamento camminando
                    1.2D   // Moltiplicatore velocità di allontanamento correndo
            ));

            // 2. Fuga dagli Ocelot (Opzionale)
            pathfinderMob.goalSelector.addGoal(3, new AvoidEntityGoal<>(
                    pathfinderMob,
                    Ocelot.class,
                    16.0F,
                    1.0D,
                    1.2D
            ));
        }
    }

    @SubscribeEvent
    public static void onWolfAttack(LivingDamageEvent.Post event) {
        // Lavoriamo solo sul lato Server
        if (event.getEntity().level().isClientSide()) return;
        if (!ModUtils.isGameRunningWithCataclysm(event.getEntity().level(), AllCataclysms.ETERNAL_ECLIPSE)) return;
        if (!EternalEclipseStage.isBloodMoonEventActiveOnBiome((ServerLevel) event.getEntity().level(),
                ModUtils.getBiomeID(event.getEntity().level(), event.getEntity().getOnPos()))) return;

        // 1. Verifichiamo se l'aggressore (TrueSource) è un Lupo
        if (event.getSource().getEntity() instanceof Wolf wolf) {

            // 2. Verifichiamo se la vittima che subisce il danno è un Mostro
            if (event.getEntity() instanceof Monster) {

                // 3. Otteniamo l'ammontare del danno inflitto nell'attacco
                float damageDealt = event.getOriginalDamage();

                // 4. Calcoliamo il 30% del danno (0.3F)
                float healAmount = damageDealt * 0.45F;

                if (healAmount > 0) {
                    wolf.heal(healAmount);

                    // Verifichiamo che il livello sia un ServerLevel prima di spawnare le particelle
                    if (wolf.level() instanceof ServerLevel serverLevel) {

                        // Parametri di sendParticles:
                        // 1. Tipo di particella: ParticleTypes.HAPPY_VILLAGER
                        // 2, 3, 4. Posizione (X, Y + offset per la testa, Z)
                        // 5. Quantità di particelle da generare (es. 5)
                        // 6, 7, 8. Dispersione (offset casuale X, Y, Z per distribuirle nell'aria)
                        // 9. Velocità delle particelle

                        serverLevel.sendParticles(
                                ParticleTypes.HAPPY_VILLAGER,
                                wolf.getX(),
                                wolf.getY() + 0.8, // Alziamo la coordinata Y per farle uscire vicino alla testa
                                wolf.getZ(),
                                5,    // Genera 5 particelle
                                0.3,  // Dispersione X
                                0.3,  // Dispersione Y
                                0.3,  // Dispersione Z
                                0.02  // Velocità
                        );
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onBloodMoonPassiveTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (entity.level().isClientSide()) return;

        // Ottimizzazione temporale: giriamo ogni secondo (20 tick)
        if (entity.tickCount % 20 != 0) return;

        ServerLevel level = (ServerLevel) entity.level();
        PersistentData.MapVariables globalVars = PersistentData.MapVariables.get(level);

        if (!ModUtils.isGameRunningWithCataclysm(level, AllCataclysms.ETERNAL_ECLIPSE)) return;

        BlockPos pos = entity.blockPosition();
        String biomeId = ModUtils.getBiomeID(level, pos);

        if (!globalVars.deletedBiomes.contains(biomeId)) return;
        if (!EternalEclipseStage.isBloodMoonEventActiveOnBiome(level, biomeId)) return;

        RandomSource random = level.getRandom();

        // --- LOGICA 1: ANIMALI DOCILI (EFFETTO WITHER OUTSIDE RANDOM CHANCE) ---
        if (entity instanceof Animal animal) {
            // Escludiamo categoricamente cani (Wolf) e felini (Cat, Ocelot), sia addomesticati che randagi
            if (!(animal instanceof Wolf || animal instanceof Cat || animal instanceof Ocelot)) {
                // Escludiamo cavalli e simili se addomesticati
                if (animal instanceof AbstractHorse horse && horse.isTamed()) return;
                // Escludiamo altri animali addomesticabili generici se già sottomessi
                if (animal instanceof TamableAnimal tamable && tamable.isTame()) return;

                // Se supera i filtri, applichiamo il Wither in modo costante se non ce l'ha
                if (!animal.hasEffect(MobEffects.WITHER)) {
                    animal.addEffect(new MobEffectInstance(MobEffects.WITHER, 260, 0)); // 13 secondi
                }
            }else{
                animal.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 1));
                animal.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1));
                animal.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 1));
                if (animal instanceof Wolf wolf){
                    // 1. Facciamo arrabbiare il lupo visivamente/logicamente
                    // 1. Resetta il target attuale e il timer di rabbia
                    wolf.setPersistentAngerTarget(null);
                    wolf.setRemainingPersistentAngerTime(0);
                    wolf.setTarget(null); // Rimuove l'eventuale bersaglio corrente

                    // 2. Aggiungiamo il Goal per attaccare i mostri ostili (esclusi i Creeper)
                    wolf.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(
                            wolf,
                            Monster.class, // Cerca qualsiasi classe che estende Monster (Zombie, Scheletri, Ragni, ecc.)
                            10,            // Intervallo di controllo (ogni 10 tick)
                            true,          // Deve vedere il bersaglio (line of sight)
                            false,         // Non deve essere necessariamente vicino
                            (target) -> !(target instanceof Creeper) // FILTRO: Esclude i Creeper!
                    ));
                }
            }
        }

        // --- LOGICA 2: TRASFORMAZIONI DA FULMINE (CON IMMUNITÀ DI 3 SECONDI = 60 TICK) ---
        // --- LOGICA 2: GESTIONE FULMINI (CON IMMUNITÀ DI 3 SECONDI = 60 TICK) ---
        if (entity.tickCount > 60) {

            // 2A. EPURAZIONE DEI MOB DEBOLI (NON GENERATI DALLA LUNA DI SANGUE)
            if (entity instanceof Mob mob) {
                boolean isWeak = false;

                // RAGNI: Se non è invisibile, è debole
                if (mob instanceof net.minecraft.world.entity.monster.Spider) {
                    if (!mob.hasEffect(MobEffects.INVISIBILITY)) {
                        isWeak = true;
                    }
                }
                // ZOMBIE E SCHELETRI: Se non hanno pezzi di armatura addosso, sono deboli
                else if (mob instanceof net.minecraft.world.entity.monster.Zombie || mob instanceof net.minecraft.world.entity.monster.AbstractSkeleton) {
                    ItemStack helmet = mob.getItemBySlot(EquipmentSlot.HEAD);
                    ItemStack chest = mob.getItemBySlot(EquipmentSlot.CHEST);

                    // Se mancano l'elmo o la corazza, consideriamo il mob inadeguato per la Luna di Sangue
                    if (helmet.isEmpty() || chest.isEmpty()) {
                        if (random.nextFloat() < 0.2F) {
                            isWeak = true;
                        }
                    }
                }

                // Se il mob è stato catalogato come debole, gli fulminiamo la testa all'istante
                if (isWeak) {
                    spawnLightningBolt(level, mob);
                    mob.kill();
                    return; // Esce dal tick per questo mob, non serve fare altro
                }
            }

            // 2B. CREEPER GIÀ CARICO: Rischio di secondo fulmine ed esplosione imminente
            if (entity instanceof Creeper creeper && creeper.isPowered()) {
                if (random.nextFloat() < 0.005F) {
                    spawnLightningBolt(level, creeper);
                    creeper.ignite();

                    AreaEffectCloud cloud = new AreaEffectCloud(level, creeper.getX(), creeper.getY(), creeper.getZ());
                    cloud.setRadius(6.0F);
                    cloud.setRadiusOnUse(-0.5F);
                    cloud.setWaitTime(10);
                    cloud.setDuration(600);
                    cloud.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 2));

                    level.addFreshEntity(cloud);
                    return;
                }
            }

            // 2C. FULMINI STANDARD E TRASFORMAZIONI (Chance del 2% ogni secondo)
            if (random.nextFloat() < 0.03F) {
                if (entity instanceof Creeper creeper) {
                    creeper.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 255));
                    if (!creeper.isPowered()) spawnLightningBolt(level, creeper);
                } else if (entity instanceof Pig pig) {
                    pig.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 255));
                    spawnLightningBolt(level, pig);
                } else if (entity instanceof Villager villager) {
                    villager.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 255));
                    spawnLightningBolt(level, villager);
                }
            }
        }
        if (entity instanceof Creeper creeper) creeper.addEffect(new MobEffectInstance(MobEffects.INFESTED, 200));
        else if (entity instanceof Spider spider) spider.addEffect(new MobEffectInstance(MobEffects.WEAVING, 200));
        else if (entity instanceof Bogged bogged) bogged.addEffect(new MobEffectInstance(MobEffects.OOZING, 200));
        else if (entity instanceof Skeleton skeleton) skeleton.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 1));
        else if (entity instanceof Zombie zombie) zombie.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 1));
        else if (entity instanceof Husk husk) husk.addEffect(new MobEffectInstance(MobEffects.WIND_CHARGED,200));
        else if (entity instanceof Stray stray) stray.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 1));
        else if (entity instanceof Drowned drowned) drowned.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 1));
        else if (entity instanceof Witch witch) witch.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200));

    }

    // Metodo di supporto per generare fisicamente il fulmine sull'entità
    private static void spawnLightningBolt(ServerLevel level, LivingEntity target) {
        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level);
        if (lightning != null) {
            lightning.moveTo(target.getX(), target.getY(), target.getZ());
            level.addFreshEntity(lightning);
        }
    }
    @SubscribeEvent
    public static void onAnimalDeathDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof Animal)) return; // Colpiamo solo gli animali docili

        ServerLevel level = (ServerLevel) entity.level();

        // Controlliamo se la causa del danno finale è stata l'effetto del WITHER
        if (event.getSource().is(DamageTypes.WITHER)) {

            // Verifica delle condizioni della Luna di Sangue nel bioma del decesso
            BlockPos pos = entity.blockPosition();
            Holder<Biome> biome = level.getBiome(pos);
            Optional<ResourceKey<Biome>> biomeKey = biome.unwrapKey();
            if (biomeKey.isEmpty()) return;
            String biomeId = biomeKey.get().location().toString();

            PersistentData.MapVariables globalVars = PersistentData.MapVariables.get(level);
            if (ModUtils.decodeCataclysmFromString(globalVars.cataclysm) == AllCataclysms.ETERNAL_ECLIPSE
                    && globalVars.deletedBiomes.contains(biomeId)
                    && EternalEclipseStage.isBloodMoonEventActiveOnBiome(level, biomeId)) {

                // Rimuoviamo dalla lista dei drop qualsiasi oggetto che sia un alimento (cibo crudo/cotto)
                event.getDrops().removeIf(itemEntity -> {
                    ItemStack stack = itemEntity.getItem();
                    // Nelle versioni stabili recenti il cibo si controlla tramite i Componenti dell'ItemStack
                    return stack.has(net.minecraft.core.component.DataComponents.FOOD);
                });
            }
        }
    }


    // 3. DECREMENTO ALLA MORTE DI CAT O OCELOT
    @SubscribeEvent
    public static void onCatOrOcelotDeath(LivingDeathEvent event) {
        // --- CASO 1: GATTO ---
        if (event.getEntity() instanceof Cat cat) {
            if (cat.isTame() && cat.getOwner() instanceof Player player) {
                if (player.level() instanceof ServerLevel serverLevel) {
                    PersistentData.MapVariables globalVars = PersistentData.MapVariables.get(serverLevel);
                    if (!(ModUtils.decodeCataclysmFromString(globalVars.cataclysm) == AllCataclysms.ETERNAL_ECLIPSE))
                        return;
                    decrementCatCount(player);
                }
            }
        }
        // --- CASO 2: OCELOT ---
        else if (event.getEntity() instanceof Ocelot ocelot) {
            CompoundTag customData = ocelot.getPersistentData();

            // Verifichiamo se l'Ocelot ha un proprietario salvato
            if (customData.hasUUID(OCELOT_OWNER_KEY)) {
                UUID ownerUuid = customData.getUUID(OCELOT_OWNER_KEY);
                Player player = ocelot.level().getPlayerByUUID(ownerUuid);
                assert player != null;
                if (player.level() instanceof ServerLevel serverLevel) {
                    PersistentData.MapVariables globalVars = PersistentData.MapVariables.get(serverLevel);
                    if (!(ModUtils.decodeCataclysmFromString(globalVars.cataclysm) == AllCataclysms.ETERNAL_ECLIPSE))
                        return;
                    // Se il giocatore è online nel mondo, decrementiamo
                    decrementCatCount(player);
                }

            }
        }
    }

    // 1. INCREMENTO QUANDO UN GATTO VIENE ADDOMESTICATO
    @SubscribeEvent
    public static void onAnimalTame(AnimalTameEvent event) {
        if (event.getAnimal() instanceof Cat && event.getTamer() instanceof Player player) {
            if (player.level() instanceof ServerLevel serverLevel){
                PersistentData.MapVariables globalVars = PersistentData.MapVariables.get(serverLevel);
                if (!(ModUtils.decodeCataclysmFromString(globalVars.cataclysm) == AllCataclysms.ETERNAL_ECLIPSE)) return;
                incrementCatCount(player);
            }
        }
    }


    private static void decrementCatCount(Player player) {
        CompoundTag nbt = player.getPersistentData();
        int currentCount = nbt.getInt(CAT_COUNT_KEY);
        // Math.max evita che il conteggio scenda sotto zero
        nbt.putInt(CAT_COUNT_KEY, Math.max(0, currentCount - 1));
    }

}