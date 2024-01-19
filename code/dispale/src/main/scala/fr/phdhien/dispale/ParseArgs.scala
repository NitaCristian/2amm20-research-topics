/*
 * This file is part of the DiSPaLe project (https://gitlab.com/phdhien/dispale)
 *
 * Copyright (c) 2022, Normandie Université, France
 *
 * Licensed under the MIT license.
 *
 * See LICENSE file in the project root for full license information.
 */
package fr.phdhien.dispale

import org.apache.commons.cli._

/* 
 * @author Arnold Hien
 */
object ParseArgs {
    def get_options(): Options = {
        val all_options: Options = new Options();
        var msg: String = ""

        //-----------------------------------------------------------------------------------
        //-----------------------------------------------------------------------------------
        
        msg = "the method to be used. Possible Values: letsip | dispale | lutom | lutomdisc \n\n"
        val method: Option = new Option("m", "method", true, msg)
        //method.setRequired(true)
        all_options.addOption(method)

        //-----------------------------------------------------------------------------------
        //-----------------------------------------------------------------------------------
        
        msg = "dataset file \n\n"
        val dataset: Option = new Option("d", "data", true, msg)
        //val dataset: Option = new Option("d", "data", false, "dataset file")
        //dataset.setRequired(true)
        all_options.addOption(dataset)

        //-----------------------------------------------------------------------------------
        //-----------------------------------------------------------------------------------

        msg = "fimi dataset file \n\n"
        val fimi_dataset: Option = new Option("FI", "fimi", true, msg)
        //fimi_dataset.setRequired(true)
        all_options.addOption(fimi_dataset)

        //-----------------------------------------------------------------------------------
        //-----------------------------------------------------------------------------------

        msg = "the minimum support value \n\n"
        val minsup: Option = new Option("f", "fmin", true, msg)
        //minsup.setRequired(true)
        all_options.addOption(minsup)

        //-----------------------------------------------------------------------------------
        //-----------------------------------------------------------------------------------
        
        msg = "the query size (nb patterns to be mine in each iteration) \n\n"
        val querySize: Option = new Option("k", "query", true, msg)
        //querySize.setRequired(true)
        all_options.addOption(querySize)

        //-----------------------------------------------------------------------------------
        //-----------------------------------------------------------------------------------
        
        msg = "#iterations \n\n"
        val iterations: Option = new Option("i", "iter", true, msg)
        //iterations.setRequired(true)
        all_options.addOption(iterations)

        //-----------------------------------------------------------------------------------
        //-----------------------------------------------------------------------------------
        
        msg = "the rank function. Example : frequencyranker, surprisingnessranker \n\n"
        val rankFunction: Option = new Option("r", "rank", true, msg)
        //rankFunction.setRequired(true)
        all_options.addOption(rankFunction)

        //-----------------------------------------------------------------------------------
        //-----------------------------------------------------------------------------------
        
        msg = "the #patterns of current iteration to keep for next iteration\n"
        msg += "Value must be less than k value \n\n"
        val queryRetention: Option = new Option("l", "retention", true, msg)
        //queryRetention.setRequired(true)
        all_options.addOption(queryRetention)

        //-----------------------------------------------------------------------------------
        //-----------------------------------------------------------------------------------
        
        msg = "the features used to represent the patterns (separated by `-')\n"
        msg += "Values: Items, Transactions, Frequency, Length. Example: Item or Frquency-Transactions \n\n"
        val features: Option = new Option("F", "features", true, msg)
        //features.setRequired(true)
        all_options.addOption(features)

        //-----------------------------------------------------------------------------------
        //-----------------------------------------------------------------------------------
        
        msg = "set how the features should be updated. Value in : [ALL, RND] \n\n"
        val features_updt: Option = new Option("FU", "features-update", true, msg)
        //features_updt.setRequired(true)
        all_options.addOption(features_updt)

        //-----------------------------------------------------------------------------------
        //-----------------------------------------------------------------------------------
        
        msg = "tilt parameter for the weight function. Value between 0 and 1 \n\n"
        val tilt: Option = new Option("t", "tilt", true, msg)
        //tilt.setRequired(true)
        all_options.addOption(tilt)

        //-----------------------------------------------------------------------------------
        //-----------------------------------------------------------------------------------
        
        msg = "the random seed \n\n"
        val seed: Option = new Option("s", "seed", true, msg)
        //seed.setRequired(true)
        all_options.addOption(seed)

        //-----------------------------------------------------------------------------------
        //-----------------------------------------------------------------------------------
        
        msg = "the agregation method\n Values: LIN or EXP. Default value: LIN \n\n"
        val agregation: Option = new Option("ag", "aggregation", true, msg)
        //agregation.setRequired(true)
        all_options.addOption(agregation)

        //-----------------------------------------------------------------------------------
        //-----------------------------------------------------------------------------------
        
        msg = "the value used by the aggregation function. Default Value : 0.15 \n\n"
        val eta_value: Option = new Option("e", "eta", true, msg)
        //eta_value.setRequired(true)
        all_options.addOption(eta_value)

        //-----------------------------------------------------------------------------------
        //-----------------------------------------------------------------------------------
        
        msg = "the mining approach used to extract patterns. Default Value : Flexics \n\n"
        val oracle: Option = new Option("o", "oracle", true, msg)
        //oracle.setRequired(true)
        all_options.addOption(oracle)

        //-----------------------------------------------------------------------------------
        //-----------------------------------------------------------------------------------
        
        msg = "the algo used to mine patterns over iterations. Default Value : EFlexics \n\n"
        val algo: Option = new Option("a", "algo", true, msg)
        //oracle.setRequired(true)
        all_options.addOption(algo)
        
        //-----------------------------------------------------------------------------------
        //-----------------------------------------------------------------------------------
        
        val weightsFile: Option= new Option("w", "weights", true, "the weights file \n\n")
        all_options.addOption(weightsFile)

        //-----------------------------------------------------------------------------------
        //-----------------------------------------------------------------------------------
        
        val help: Option = new Option("h", "help", true, "help \n")
        //help.setRequired(true)
        all_options.addOption(help)

        //-----------------------------------------------------------------------------------
        //-----------------------------------------------------------------------------------
        
        all_options
    }

    //############################################################################################################
    //############################################################################################################

    def get_parse(args: Array[String]) = {
        val all_options: Options = get_options()
        var cmd: CommandLine = null
        val parser: CommandLineParser = new DefaultParser()
        val formatter: HelpFormatter = new HelpFormatter()
        
        var help_msg = "\n\n\t\t***** HELP *****\n\tlist of arguments\n\n"
        var error_msg = "\nError : missing arguments"
        
        try {
            cmd = parser.parse(all_options, args)
            
            if (
                    cmd.hasOption("h")
                ) {
                formatter.printHelp("utility-name", all_options)
                System.exit(0)
            }
            else if (
                        !cmd.hasOption("m") || !cmd.hasOption("d") || !cmd.hasOption("f") || !cmd.hasOption("F") ||
                        !cmd.hasOption("k") || !cmd.hasOption("l") || !cmd.hasOption("r") || 
                        !cmd.hasOption("o") || !cmd.hasOption("a") || !cmd.hasOption("i")
                    ) {
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

