@file:JvmName("NEAT")
@file:JvmMultifileClass

package eu.timerertim.knevo.neat

import eu.timerertim.knevo.activation.Tanh
import eu.timerertim.knevo.neat.config.NEATConfig
import eu.timerertim.knevo.neat.config.NEATDefaults
import java.util.*
import javax.management.RuntimeErrorException
import kotlin.math.abs
import kotlin.random.Random

private val f = Tanh()

typealias Network = NEATNetwork

class NEATNetwork(private val config: NEATConfig) : eu.timerertim.knevo.Genome, Cloneable {
    override var doReset: Boolean = true

    // Global Percentile Rank (higher the better)
    override var fitness: Float = 0.toFloat()
    var points: Float = 0.toFloat()

    // DNA- Main archive of gene information
    var connectionGeneList = ArrayList<ConnectionGene>()

    // Generated while performing network operation
    private val nodes =
        TreeMap<Int, NEATNode>()

    // For number of child to breed in species
    var adjustedFitness: Float = 0f

    val mutationRates = HashMap<MutationKeys, Float>()

    enum class MutationKeys {
        STEPS,
        PERTURB_CHANCE,
        WEIGHT_CHANCE,
        WEIGHT_MUTATION_CHANCE,
        NODE_MUTATION_CHANCE,
        CONNECTION_MUTATION_CHANCE,
        BIAS_CONNECTION_MUTATION_CHANCE,
        DISABLE_MUTATION_CHANCE,
        ENABLE_MUTATION_CHANCE
    }

    init {
        mutationRates[MutationKeys.STEPS] = NEATDefaults.STEPS
        mutationRates[MutationKeys.PERTURB_CHANCE] = NEATDefaults.PERTURB_CHANCE
        mutationRates[MutationKeys.WEIGHT_CHANCE] = NEATDefaults.WEIGHT_CHANCE
        mutationRates[MutationKeys.WEIGHT_MUTATION_CHANCE] = NEATDefaults.WEIGHT_MUTATION_CHANCE
        mutationRates[MutationKeys.NODE_MUTATION_CHANCE] = NEATDefaults.NODE_MUTATION_CHANCE
        mutationRates[MutationKeys.CONNECTION_MUTATION_CHANCE] = NEATDefaults.CONNECTION_MUTATION_CHANCE
        mutationRates[MutationKeys.BIAS_CONNECTION_MUTATION_CHANCE] = NEATDefaults.BIAS_CONNECTION_MUTATION_CHANCE
        mutationRates[MutationKeys.DISABLE_MUTATION_CHANCE] = NEATDefaults.DISABLE_MUTATION_CHANCE
        mutationRates[MutationKeys.ENABLE_MUTATION_CHANCE] = NEATDefaults.ENABLE_MUTATION_CHANCE
    }

    // todo: improve
    public override fun clone(): NEATNetwork {
        val genome = NEATNetwork(config)

        for (c in connectionGeneList) {
            genome.connectionGeneList.add(c.clone())
        }

        genome.fitness = fitness
        genome.adjustedFitness = adjustedFitness

        genome.mutationRates.clear()

        mutationRates.forEach { key, value ->
            genome.mutationRates[key] = value
        }

        return genome
    }

    fun reset() {
        doReset = true
    }

    private fun generateNetwork() {
        if (!doReset) return

        nodes.clear()
        //  Input layer
        for (i in 0 until config.inputs) {
            nodes[i] = NEATNode(0f)                    //Inputs
        }
        nodes[config.inputs] = NEATNode(1f)        // Bias
        nodes.values.forEach { it.isActivated = true }

        //output layer
        for (i in config.inputs until config.inputs + config.outputs) {
            nodes[i] = NEATNode(0f)
        }

        // hidden layer
        for (con in connectionGeneList) {
            if (!nodes.containsKey(con.from))
                nodes[con.from] = NEATNode(0f)
            if (!nodes.containsKey(con.to))
                nodes[con.to] = NEATNode(0f)
            nodes[con.to]!!.connections.add(con)
        }

        doReset = false
    }

    override operator fun invoke(inputs: FloatArray): FloatArray {
        val output = FloatArray(config.outputs)
        generateNetwork()

        for (i in 0 until config.inputs) {
            nodes[i]!!.value = inputs[i]
        }

        for ((id, node) in nodes.entries) {
            if (id > config.inputs)
                node.isActivated = false
        }

        for (i in 0 until config.outputs) {
            output[i] = nodes[config.inputs + i]!!.getValue()
        }
        return output
    }

    private fun NEATNode.getValue(): Float {
        if (isActivated) {
            return value
        }

        isActivated = true
        var sum = 0F
        val enabledConnections = connections.filter { it.isEnabled }
        for (con in enabledConnections) {
            sum += nodes[con.from]!!.getValue() * con.weight
        }
        val value = if (enabledConnections.isNotEmpty()) f(sum) else sum
        this.value = value
        return value
    }


    // Mutations

    fun mutate() {
        // mutate mutation rates
        for ((key, value) in mutationRates) {
            if (Random.nextBoolean()) {
                mutationRates[key] = 0.95f * value
            } else {
                mutationRates[key] = 1.05263f * value
            }
        }

        if (Random.nextFloat() <= mutationRates[MutationKeys.WEIGHT_MUTATION_CHANCE]!!) {
            mutateWeight()
        }

        if (Seed.random.nextFloat() <= mutationRates[MutationKeys.CONNECTION_MUTATION_CHANCE]!!) {
            mutateAddConnection(false)
        }

        if (Seed.random.nextFloat() <= mutationRates[MutationKeys.BIAS_CONNECTION_MUTATION_CHANCE]!!) {
            mutateAddConnection(true)
        }

        if (Seed.random.nextFloat() <= mutationRates[MutationKeys.NODE_MUTATION_CHANCE]!!) {
            mutateAddNode()
        }

        if (Seed.random.nextFloat() <= mutationRates[MutationKeys.DISABLE_MUTATION_CHANCE]!!) {
            disableMutate()
        }

        if (Seed.random.nextFloat() <= mutationRates[MutationKeys.ENABLE_MUTATION_CHANCE]!!) {
            enableMutate()
        }

        doReset = true
    }

    private fun mutateWeight() {
        for (c in connectionGeneList) {
            if (Seed.random.nextFloat() < NEATDefaults.WEIGHT_CHANCE) {
                if (Seed.random.nextFloat() < NEATDefaults.PERTURB_CHANCE)
                    c.weight = c.weight + (2 * Seed.random.nextFloat() - 1) * NEATDefaults.STEPS
                else
                    c.weight = 4 * Seed.random.nextFloat() - 2
            }
        }
    }

    private fun mutateAddConnection(forceBias: Boolean) {
        generateNetwork()
        val random2 = Seed.random.nextInt(nodes.size - config.inputs - config.outputs + 1) +
                config.inputs + config.outputs - 1
        var random1 = Seed.random.nextInt(nodes.size)
        if (forceBias)
            random1 = config.inputs

        val node1 = nodes.keys.elementAtOrNull(random1)
        val node2 = nodes.keys.elementAtOrNull(random2)

        for (con in nodes[node2]?.connections ?: emptyList()) {
            if (con.from == node1)
                return
        }

        if (node1 == null || node2 == null)
            throw RuntimeErrorException(null)          // TODO Pool.newInnovation(node1, node2)

        if (node1 >= node2) return

        connectionGeneList.add(
            ConnectionGene(
                node1,
                node2,
                4 * Seed.random.nextFloat() - 2,
                true
            )
        )
    }

    internal fun mutateAddNode() {
        generateNetwork()
        val enabledConnections = connectionGeneList.filter { it.isEnabled }
        if (enabledConnections.isNotEmpty()) {
            val randomCon = enabledConnections.random()
            val nextNode = nodes.size
            randomCon.isEnabled = false
            connectionGeneList.add(
                ConnectionGene(
                    randomCon.from,
                    nextNode,
                    1f,
                    true
                )
            )        // Add innovation and weight
            connectionGeneList.add(
                ConnectionGene(
                    nextNode,
                    randomCon.to,
                    randomCon.weight,
                    true
                )
            )
        }
    }

    private fun disableMutate() {
        if (connectionGeneList.isNotEmpty()) {
            val gene = connectionGeneList.random(Seed.random)
            gene.isEnabled = false
        }
    }

    private fun enableMutate() {
        if (connectionGeneList.isNotEmpty()) {
            val gene = connectionGeneList.random(Seed.random)
            gene.isEnabled = true
        }
    }

    override fun toString(): String {
        return "Genome{" +
                "fitness=" + fitness +
                ", connectionGeneList=" + connectionGeneList +
                ", nodeGenes=" + nodes +
                '}'.toString()
    }

    fun adjustFitness(size: Int) {
        adjustedFitness = fitness / size
    }

    companion object {

        fun crossOver(parent1: NEATNetwork, parent2: NEATNetwork): NEATNetwork {
            val (fitParent, loserParent) = if (parent1.fitness < parent2.fitness) {
                parent2 to parent1
            } else {
                parent1 to parent2
            }

            val child = NEATNetwork(fitParent.config)
            val geneMap1 = TreeMap<Long, ConnectionGene>()
            val geneMap2 = TreeMap<Long, ConnectionGene>()

            for (con in fitParent.connectionGeneList) {
                geneMap1[con.innovation] = con
            }

            for (con in loserParent.connectionGeneList) {
                geneMap2[con.innovation] = con
            }

            val innovationP1 = geneMap1.keys
            val innovationP2 = geneMap2.keys

            val allInnovations = HashSet(innovationP1)
            allInnovations.addAll(innovationP2)

            for (key in allInnovations) {
                val trait: ConnectionGene?

                if (geneMap1.containsKey(key) && geneMap2.containsKey(key)) {
                    trait = if (Seed.random.nextBoolean()) {
                        geneMap1[key]?.clone()
                    } else {
                        geneMap2[key]?.clone()
                    }

                    if (geneMap1[key]!!.isEnabled != geneMap2[key]!!.isEnabled) {
                        trait?.isEnabled = Seed.random.nextFloat() >= 0.75f
                    }

                } else if (fitParent.fitness == loserParent.fitness) {               // disjoint or excess and equal fitness
                    trait = if (geneMap1.containsKey(key))
                        geneMap1[key]
                    else
                        geneMap2[key]

                    if (Seed.random.nextBoolean()) {
                        continue
                    }

                } else
                    trait = geneMap1[key]


                if (trait != null) {
                    child.connectionGeneList.add(trait)
                }
            }

            // todo: should not need .clone()
            return child.clone()
        }


        fun isSameSpecies(g1: NEATNetwork, g2: NEATNetwork): Boolean {
            val geneMap1 = TreeMap<Long, ConnectionGene>()
            val geneMap2 = TreeMap<Long, ConnectionGene>()

            var matching = 0
            var disjoint = 0
            var weight = 0f
            var delta = 0f

            for (con in g1.connectionGeneList) {
                geneMap1[con.innovation] = con
            }

            for (con in g2.connectionGeneList) {
                geneMap2[con.innovation] = con
            }

            val innovationP1 = geneMap1.keys
            val innovationP2 = geneMap2.keys

            val allInnovations = HashSet(innovationP1)
            allInnovations.addAll(innovationP2)

            for (key in allInnovations) {

                if (geneMap1.containsKey(key) && geneMap2.containsKey(key)) {
                    matching++
                    weight += abs(geneMap1[key]!!.weight - geneMap2[key]!!.weight)
                } else {
                    disjoint++
                }

            }

            //System.out.println("matching : "+matching + "\ndisjoint : "+ disjoint + "\nExcess : "+ excess +"\nWeight : "+ weight);

            val N = matching + disjoint

            if (N > 0)
                delta =
                    (NEATDefaults.DISJOINT_COEFFICENT * disjoint) / N + NEATDefaults.WEIGHT_COEFFICENT * weight / matching

            return delta < NEATDefaults.COMPATIBILITY_THRESHOLD

        }
    }
}