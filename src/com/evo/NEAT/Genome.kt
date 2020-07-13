package com.evo.NEAT

import com.evo.NEAT.config.Config
import com.evo.NEAT.config.Seed

import javax.management.RuntimeErrorException
import java.io.*
import java.util.*

class Genome : Comparable<Genome> {
    var fitness: Float =
        0.toFloat()                                          // Global Percentile Rank (higher the better)
    var points: Float = 0.toFloat()
    // Can remove below setter-getter after testing
    var connectionGeneList = ArrayList<ConnectionGene>()           // DNA- MAin archive of gene information
    private val nodes =
        TreeMap<Int, NodeGene>()                          // Generated while performing network operation
    var adjustedFitness: Float =
        0.toFloat()                                      // For number of child to breed in species

    private var mutationRates = HashMap<MutationKeys, Float>()

    private enum class MutationKeys {
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

    constructor() {
        this.mutationRates[MutationKeys.STEPS] = Config.STEPS
        this.mutationRates[MutationKeys.PERTURB_CHANCE] = Config.PERTURB_CHANCE
        this.mutationRates[MutationKeys.WEIGHT_CHANCE] = Config.WEIGHT_CHANCE
        this.mutationRates[MutationKeys.WEIGHT_MUTATION_CHANCE] = Config.WEIGHT_MUTATION_CHANCE
        this.mutationRates[MutationKeys.NODE_MUTATION_CHANCE] = Config.NODE_MUTATION_CHANCE
        this.mutationRates[MutationKeys.CONNECTION_MUTATION_CHANCE] = Config.CONNECTION_MUTATION_CHANCE
        this.mutationRates[MutationKeys.BIAS_CONNECTION_MUTATION_CHANCE] = Config.BIAS_CONNECTION_MUTATION_CHANCE
        this.mutationRates[MutationKeys.DISABLE_MUTATION_CHANCE] = Config.DISABLE_MUTATION_CHANCE
        this.mutationRates[MutationKeys.ENABLE_MUTATION_CHANCE] = Config.ENABLE_MUTATION_CHANCE
    }

    constructor(child: Genome) {

        for (c in child.connectionGeneList) {
            this.connectionGeneList.add(c.clone())
        }

        this.fitness = child.fitness
        this.adjustedFitness = child.adjustedFitness

        this.mutationRates = child.mutationRates.clone() as HashMap<MutationKeys, Float>

    }

    private fun generateNetwork() {

        nodes.clear()
        //  Input layer
        for (i in 0 until Config.INPUTS) {
            nodes[i] = NodeGene(0f)                    //Inputs
        }
        nodes[Config.INPUTS] = NodeGene(1f)        // Bias

        //output layer
        for (i in Config.INPUTS + Config.HIDDEN_NODES until Config.INPUTS + Config.HIDDEN_NODES + Config.OUTPUTS) {
            nodes[i] = NodeGene(0f)
        }

        // hidden layer
        for (con in connectionGeneList) {
            if (!nodes.containsKey(con.into))
                nodes[con.into] = NodeGene(0f)
            if (!nodes.containsKey(con.out))
                nodes[con.out] = NodeGene(0f)
            nodes[con.out]!!.connections.add(con)
        }


    }

    fun evaluateNetwork(inputs: FloatArray): FloatArray {
        val output = FloatArray(Config.OUTPUTS)
        generateNetwork()

        for (i in 0 until Config.INPUTS) {
            nodes[i]!!.value = inputs[i]
        }

        for ((key, node) in nodes) {
            var sum = 0f

            if (key > Config.INPUTS) {
                for (conn in node.connections) {
                    if (conn.isEnabled) {
                        sum += nodes[conn.into]!!.value * conn.weight
                    }
                }
                node.value = sigmoid(sum)
            }
        }

        for (i in 0 until Config.OUTPUTS) {
            output[i] = nodes[Config.INPUTS + Config.HIDDEN_NODES + i]!!.value
        }
        return output
    }

    private fun sigmoid(x: Float): Float {
        // TODO Auto-generated method stub
        return (1 / (1 + Math.exp(-4.9 * x))).toFloat()
    }

    // Mutations

    fun Mutate() {
        // Mutate mutation rates
        for ((key, value) in mutationRates) {
            if (rand.nextBoolean())
                mutationRates[key] = 0.95f * value
            else
                mutationRates[key] = 1.05263f * value
        }


        if (rand.nextFloat() <= mutationRates[MutationKeys.WEIGHT_MUTATION_CHANCE]!!)
            mutateWeight()
        if (rand.nextFloat() <= mutationRates[MutationKeys.CONNECTION_MUTATION_CHANCE]!!)
            mutateAddConnection(false)
        if (rand.nextFloat() <= mutationRates[MutationKeys.BIAS_CONNECTION_MUTATION_CHANCE]!!)
            mutateAddConnection(true)
        if (rand.nextFloat() <= mutationRates[MutationKeys.NODE_MUTATION_CHANCE]!!)
            mutateAddNode()
        if (rand.nextFloat() <= mutationRates[MutationKeys.DISABLE_MUTATION_CHANCE]!!)
            disableMutate()
        if (rand.nextFloat() <= mutationRates[MutationKeys.ENABLE_MUTATION_CHANCE]!!)
            enableMutate()
    }

    internal fun mutateWeight() {

        for (c in connectionGeneList) {
            if (rand.nextFloat() < Config.WEIGHT_CHANCE) {
                if (rand.nextFloat() < Config.PERTURB_CHANCE)
                    c.weight = c.weight + (2 * rand.nextFloat() - 1) * Config.STEPS
                else
                    c.weight = 4 * rand.nextFloat() - 2
            }
        }
    }

    internal fun mutateAddConnection(forceBais: Boolean) {
        generateNetwork()
        var i = 0
        var j = 0
        val random2 = rand.nextInt(nodes.size - Config.INPUTS - 1) + Config.INPUTS + 1
        var random1 = rand.nextInt(nodes.size)
        if (forceBais)
            random1 = Config.INPUTS
        var node1 = -1
        var node2 = -1

        for (k in nodes.keys) {
            if (random1 == i) {
                node1 = k
                break
            }
            i++
        }

        for (k in nodes.keys) {
            if (random2 == j) {
                node2 = k
                break
            }
            j++
        }
        //	System.out.println("random1 = "+random1 +" random2 = "+random2);
        //	System.out.println("Node1 = "+node1 +" node 2 = "+node2);


        if (node1 >= node2)
            return

        for (con in nodes[node2]!!.connections) {
            if (con.into == node1)
                return
        }

        if (node1 < 0 || node2 < 0)
            throw RuntimeErrorException(null)          // TODO Pool.newInnovation(node1, node2)
        connectionGeneList.add(
            ConnectionGene(
                node1,
                node2,
                InnovationCounter.newInnovation(),
                4 * rand.nextFloat() - 2,
                true
            )
        )                // Add innovation and weight

    }

    internal fun mutateAddNode() {
        generateNetwork()
        if (connectionGeneList.size > 0) {
            var timeoutCount = 0
            var randomCon = connectionGeneList[rand.nextInt(connectionGeneList.size)]
            while (!randomCon.isEnabled) {
                randomCon = connectionGeneList[rand.nextInt(connectionGeneList.size)]
                timeoutCount++
                if (timeoutCount > Config.HIDDEN_NODES)
                    return
            }
            val nextNode = nodes.size - Config.OUTPUTS
            randomCon.isEnabled = false
            connectionGeneList.add(
                ConnectionGene(
                    randomCon.into,
                    nextNode,
                    InnovationCounter.newInnovation(),
                    1f,
                    true
                )
            )        // Add innovation and weight
            connectionGeneList.add(
                ConnectionGene(
                    nextNode,
                    randomCon.out,
                    InnovationCounter.newInnovation(),
                    randomCon.weight,
                    true
                )
            )
        }
    }

    internal fun disableMutate() {
        //generateNetwork();                // remove laters
        if (connectionGeneList.size > 0) {
            val randomCon = connectionGeneList[rand.nextInt(connectionGeneList.size)]
            randomCon.isEnabled = false
        }
    }


    internal fun enableMutate() {
        //generateNetwork();                // remove laters
        if (connectionGeneList.size > 0) {
            val randomCon = connectionGeneList[rand.nextInt(connectionGeneList.size)]
            randomCon.isEnabled = true
        }
    }

    override operator fun compareTo(g: Genome): Int {
        return if (fitness == g.fitness)
            0
        else if (fitness > g.fitness)
            1
        else
            -1
    }

    override fun toString(): String {
        return "Genome{" +
                "fitness=" + fitness +
                ", connectionGeneList=" + connectionGeneList +
                ", nodeGenes=" + nodes +
                '}'.toString()
    }

    fun writeTofile() {
        var bw: BufferedWriter? = null
        var fw: FileWriter? = null
        val builder = StringBuilder()
        for (conn in connectionGeneList) {
            builder.append(conn.toString() + "\n")
        }
        try {


            fw = FileWriter("Genome.txt")
            bw = BufferedWriter(fw)
            bw.write(builder.toString())

            println("Done")

        } catch (e: IOException) {

            e.printStackTrace()

        } finally {

            try {

                bw?.close()

                fw?.close()

            } catch (ex: IOException) {

                ex.printStackTrace()

            }

        }

    }

    companion object {
        private val rand = Seed.random

        fun crossOver(parent1: Genome, parent2: Genome): Genome {
            var parent1 = parent1
            var parent2 = parent2
            if (parent1.fitness < parent2.fitness) {
                val temp = parent1
                parent1 = parent2
                parent2 = temp
            }

            val child = Genome()
            val geneMap1 = TreeMap<Int, ConnectionGene>()
            val geneMap2 = TreeMap<Int, ConnectionGene>()

            for (con in parent1.connectionGeneList) {
                assert(!geneMap1.containsKey(con.innovation))             //TODO Remove for better performance
                geneMap1[con.innovation] = con
            }

            for (con in parent2.connectionGeneList) {
                assert(!geneMap2.containsKey(con.innovation))             //TODO Remove for better performance
                geneMap2[con.innovation] = con
            }

            val innovationP1 = geneMap1.keys
            val innovationP2 = geneMap2.keys

            val allInnovations = HashSet(innovationP1)
            allInnovations.addAll(innovationP2)

            for (key in allInnovations) {
                val trait: ConnectionGene?

                if (geneMap1.containsKey(key) && geneMap2.containsKey(key)) {
                    if (rand.nextBoolean()) {
                        trait = geneMap1[key]?.clone()
                    } else {
                        trait = geneMap2[key]?.clone()
                    }

                    if (geneMap1[key]!!.isEnabled != geneMap2[key]!!.isEnabled) {
                        if (rand.nextFloat() < 0.75f)
                            trait?.isEnabled = false
                        else
                            trait?.isEnabled = true
                    }

                } else if (parent1.fitness == parent2.fitness) {               // disjoint or excess and equal fitness
                    if (geneMap1.containsKey(key))
                        trait = geneMap1[key]
                    else
                        trait = geneMap2[key]

                    if (rand.nextBoolean()) {
                        continue
                    }

                } else
                    trait = geneMap1[key]


                if (trait != null) {
                    child.connectionGeneList.add(trait)
                }
            }


            return child

        }


        fun isSameSpecies(g1: Genome, g2: Genome): Boolean {
            val geneMap1 = TreeMap<Int, ConnectionGene>()
            val geneMap2 = TreeMap<Int, ConnectionGene>()

            var matching = 0
            var disjoint = 0
            var excess = 0
            var weight = 0f
            val lowMaxInnovation: Int
            var delta = 0f

            for (con in g1.connectionGeneList) {
                assert(!geneMap1.containsKey(con.innovation))             //TODO Remove for better performance
                geneMap1[con.innovation] = con
            }

            for (con in g2.connectionGeneList) {
                assert(!geneMap2.containsKey(con.innovation))             //TODO Remove for better performance
                geneMap2[con.innovation] = con
            }
            if (geneMap1.isEmpty() || geneMap2.isEmpty())
                lowMaxInnovation = 0
            else
                lowMaxInnovation = Math.min(geneMap1.lastKey(), geneMap2.lastKey())

            val innovationP1 = geneMap1.keys
            val innovationP2 = geneMap2.keys

            val allInnovations = HashSet(innovationP1)
            allInnovations.addAll(innovationP2)

            for (key in allInnovations) {

                if (geneMap1.containsKey(key) && geneMap2.containsKey(key)) {
                    matching++
                    weight += Math.abs(geneMap1[key]!!.weight - geneMap2[key]!!.weight)
                } else {
                    if (key < lowMaxInnovation) {
                        disjoint++
                    } else {
                        excess++
                    }
                }

            }

            //System.out.println("matching : "+matching + "\ndisjoint : "+ disjoint + "\nExcess : "+ excess +"\nWeight : "+ weight);

            val N = matching + disjoint + excess

            if (N > 0)
                delta =
                    (Config.EXCESS_COEFFICENT * excess + Config.DISJOINT_COEFFICENT * disjoint) / N + Config.WEIGHT_COEFFICENT * weight / matching

            return delta < Config.COMPATIBILITY_THRESHOLD

        }
    }

}