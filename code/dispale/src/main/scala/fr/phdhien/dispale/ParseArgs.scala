package fr.phdhien.dispale

import org.apache.commons.cli._

object ParseArgs {


  def get_options() = {
    val all_options: Options = new Options();

    val dataset: Option = new Option("d", "data", true, "dataset file --> REQUIRED \n\n")
    //val dataset: Option = new Option("d", "data", false, "dataset file")
    //dataset.setRequired(true)
    all_options.addOption(dataset)

    val fimi_dataset: Option = new Option("FI", "fimi", true, "fimi dataset file --> REQUIRED \n\n")
    //val fimi_dataset: Option = new Option("FI", "fimi", false, "fimi dataset file")
    //fimi_dataset.setRequired(true)
    all_options.addOption(fimi_dataset)

    val minsup: Option = new Option("f", "fmin", true, "the minimum support value --> REQUIRED \n\n")
    //val minsup: Option = new Option("f", "fmin", false, "the minimum support value")
    //minsup.setRequired(true)
    all_options.addOption(minsup)

    val querySize: Option = new Option("k", "query", true, "the query size (nb patterns to be mine in each iteration) --> REQUIRED \n\n")
    //val querySize: Option = new Option("k", "query", false, "the query size (nb patterns to be mine in each iteration)")
    //querySize.setRequired(true)
    all_options.addOption(querySize)

    val iterations: Option = new Option("i", "iter", true, "nb of iterations --> REQUIRED \n\n")
    //val iterations: Option = new Option("i", "iter", false, "nb of iterations")
    //iterations.setRequired(true)
    all_options.addOption(iterations)

    val rankFunction: Option = new Option("r", "rank", true, "the rank function --> REQUIRED\nRxample : frequencyranker, surprisingnessranker \n\n")
    //val rankFunction: Option = new Option("r", "rank", false, "the rank function")
    //rankFunction.setRequired(true)
    all_options.addOption(rankFunction)

    val queryRetention: Option = new Option("l", "retention", true, "the nb of patterns of current iteration to keep for next iteration --> REQUIRED\nValue must be less than k value \n\n")
    //val queryRetention: Option = new Option("l", "retention", true, "the nb of patterns of current iteration to keep for next iteration")
    //queryRetention.setRequired(true)
    all_options.addOption(queryRetention)

    val features: Option = new Option("F", "features", true, "the features used to represent the patterns (separated by -) --> REQUIRED\nExample: Item or Frquency-Transactions\nValues: Items, Transactions, Frequency, Length \n\n")
    //val features: Option = new Option("F", "features", false, "the features used to represent the patterns (separated by -)")
    //features.setRequired(true)
    all_options.addOption(features)

    val features_updt: Option = new Option("FU", "features-update", true, "set how the features should be updated\nREQUIRED\nValues : ALL or RND \n\n")
    //val features_updt: Option = new Option("FU", "features-update", false, "set how the features should be updated (ALL or RND)")
    //features_updt.setRequired(true)
    all_options.addOption(features_updt)

    val tilt: Option = new Option("t", "tilt", true, "tilt parameter for the weight function --> REQUIRED \n value between 0 and 1 \n\n")
    //val tilt: Option = new Option("t", "tilt", false, "tilt parameter for the weight function")
    //tilt.setRequired(true)
    all_options.addOption(tilt)

    val seed: Option = new Option("s", "seed", true, "the random seed \n\n")
    //val seed: Option = new Option("s", "seed", false, "the random seed")
    //seed.setRequired(true)
    all_options.addOption(seed)

    val method: Option = new Option("m", "method", true, "the method to be used --> REQUIRED\nletsip original or dispale (using discriminating patterns) \n\n")
    //val method: Option = new Option("m", "method", false, "the method to be used")
    //method.setRequired(true)
    all_options.addOption(method)

    val agregation: Option = new Option("a", "aggregation", true, "the agregation method\n Values: LIN or EXP\nDefault value: LIN \n\n")
    //val agregation: Option = new Option("a", "aggregation", true, "the agregation method (SUM or EXP)")
    //agregation.setRequired(true)
    all_options.addOption(agregation)

    val eta_value: Option = new Option("e", "eta", true, "the value used by the aggregation function\nDefault Value : 0.15 \n\n")
    //val eta_value: Option = new Option("e", "eta", false, "the value used by the aggregation function")
    //eta_value.setRequired(true)
    all_options.addOption(eta_value)

    val jmax: Option = new Option("j", "jmax", true, "the maximum jaccard theshold - Default value : 0.05 \n\n")
    //val jmax: Option = new Option("j", "jmax", false, "the maximum jaccard theshold")
    //jmax.setRequired(true)
    all_options.addOption(jmax)

    val oracle: Option = new Option("o", "oracle", true, "the oracle used to mine patterns over iterations\nDefault Value : EFlexics \n\n")
    //val oracle: Option = new Option("o", "oracle", false, "the oracle used to mine patterns over iterations")
    //oracle.setRequired(true)
    all_options.addOption(oracle)
    
    val directory: Option= new Option("dir", "directory",true,"HAI Sampler parameters dir --> REQUIRED \n\n")
    all_options.addOption(directory)
    
    val run_id: Option= new Option("run", "run",true,"the run id --> REQUIRED \n\n")
    all_options.addOption(run_id)
    
    val weightsFile: Option= new Option("w", "weights", false, "the weights file \n\n")
    all_options.addOption(weightsFile)

    val help: Option = new Option("h", "help", false, "help \n")
    //help.setRequired(true)
    all_options.addOption(help)

    all_options
  }

  def get_parse(args: Array[String]) = {
    val all_options: Options = get_options()
    var cmd: CommandLine = null
    val parser: CommandLineParser = new DefaultParser()
    val formatter: HelpFormatter = new HelpFormatter()
    
    var help_msg = "\n\n\t\t***** HELP *****\n\tlist of arguments\n\n"
    var error_msg = "\nError : missing arguments"
    
    try {
      cmd = parser.parse(all_options, args)
      
      if (cmd.hasOption("h")) {
        formatter.printHelp("utility-name", all_options)
        System.exit(0)
      }
      else if (!cmd.hasOption("d") || !cmd.hasOption("FI") || !cmd.hasOption("f") || !cmd.hasOption("k")
                       || !cmd.hasOption("i") || !cmd.hasOption("r") || !cmd.hasOption("F") || !cmd.hasOption("m")
                        || !cmd.hasOption("l") || !cmd.hasOption("t") || !cmd.hasOption("d")) {
        
        formatter.printHelp(help_msg, all_options)
        System.exit(0)
        
      }
      
    } catch {
      case e: ParseException => 
        e.printStackTrace()
        println(e.getMessage())
        // formatter.printHelp("utility-name", all_options)
        formatter.printHelp(error_msg, all_options)
        System.exit(1)
      case _: Throwable => 
        println("Undefined error")
        // formatter.printHelp("utility-name", all_options)
        formatter.printHelp(error_msg, all_options)
        System.exit(1)
        
    }

    cmd

  }

}

