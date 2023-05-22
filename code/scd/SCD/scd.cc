//  Copyright 2009 Shai Shalev-Shwartz, Ambuj Tewari

//  SCD version 2.1

/*
    This file is part of SCD.

    SCD is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    SCD is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with SCD. If not, see <http://www.gnu.org/licenses/>.
*/

#include <iostream>
#include <fstream>
#include <cstdio>
#include <cstdlib>
#include <cmath>
#include <vector>
#include <sstream>
#include <ctime>
#include <string>

#include "cmd_line.h"
#include "Losses.h"

class IndexValuePair {
public:
	IndexValuePair() { }
	IndexValuePair(uint i, double v) : first(i), second(v) { }

	uint first;
	double second;
};


// a sparse vector is just a vector of index, value pairs
typedef std::vector<IndexValuePair> sparse_vector;

void print_summary(std::vector<double>& innerprod_with_w,
		std::vector<double>& labels,
		std::vector<double>& w,
		int num_examples,
		double lambda,
		Losses& L) {

	double sum=0;
	double w_norm = 0.0;
	uint w_density = 0;
	int j;

	for (j=-0; j<w.size(); j++) {
		double tmp = fabs(w[j]);
		w_norm += tmp;
		if (tmp > 0.0000001) w_density++;
	}

	for (j=0; j < num_examples; j++) {
		sum += L.loss(innerprod_with_w[j],labels[j]);
	}

	std::cout << w_norm << ' ' << w_density << ' ' << 
		sum << ' ' << sum / num_examples + lambda * w_norm << std::endl;
}


int main(int argc, char **argv) {

	std::string examples_filename, labels_filename;

	// loss that the user wants to use
	int loss_type;

	// number of iterations for which to run SCD
	int num_iters;

	// print summary every so many iterations;
	int print_me;
	
	// regularization parameter
	double lambda;

	// parse command line
	learning::cmd_line cmdline;
#ifdef _GREEDY_
	cmdline.info("L1 regularized loss minimization using greedy coordinate descent");
#else
#ifdef _CYCLIC_
	cmdline.info("L1 regularized loss minimization using cyclic coordinate descent");
#else
	cmdline.info("L1 regularized loss minimization using stochastic coordinate descent");
#endif
#endif
	cmdline.add_master_option("<data-file>",&examples_filename);
	cmdline.add_master_option("<label-file>",&labels_filename);
	cmdline.add("-lambda", "regularization parameter (default = 0.001)",&lambda,0.001);
	cmdline.add("-loss","0 for logistic, 2 for squared (default = 0)",&loss_type,0);
	cmdline.add("-iters","number of iterations (default = 1000)", &num_iters, 1000);
	cmdline.add("-printout","after how many iterations to print summary (default 1000)",&print_me,1000);

	int rc = cmdline.parse(argc, argv);
	if (rc < 2) {
		cmdline.print_help();
		return( EXIT_FAILURE );
	}


	// choose seed from current time
	srand(time(NULL));

	// set the loss function
	if (loss_type != 0 && loss_type != 2) {
		std::cerr << "Only logistic and squared losses are supported" << std::endl;
	}

	Losses L(loss_type);

	// labels stored as a vector
	std::vector<double> labels;

	// start reading the examples and labels
	// the examples file should look like :-
	// (m = # of examples, d = dimension)
	//
	// m d
	// i1 v1 i2 v2 i3 v3 ...  <-- first dimension or feature
	// i1 v1 i2 v2 i3 v3 ...  <-- second dimension or feature
	// .
	// .
	// i1 v1 i2 v2 i3 v3 ...  <-- last dimension or feature
	//
	// indices are in the range [0 .. m-1 ]

	// open examples file
	std::ifstream examples_file(examples_filename.c_str());
	if (!examples_file.good()) {
		std::cerr << "Error reading " << examples_filename << std::endl;
		return( EXIT_FAILURE );
	}

	// no. of examples, features in the dataset
	int num_examples, num_features;

	// read the examples file
	std::string buffer;

	getline(examples_file,buffer);
	std::istringstream istr1(buffer);
	istr1 >> num_examples;
	istr1 >> num_features;

	// examples stored feature-wise, each dimension being a sparse vector
	std::vector<sparse_vector> examples(num_features);

	int line_count=0;
	int curr_index;
	double curr_value;

	while (getline(examples_file,buffer) && line_count < num_features) {
		line_count++;

		sparse_vector& curr_feature = examples[line_count-1];

		// read the index, value pairs from buffer
		std::istringstream istr2(buffer);
		while (istr2 >> curr_index) {
			istr2 >> curr_value;

			if (curr_index < 0 || curr_index > num_examples-1) {
				std::cerr << "Illegal index value " << curr_index << " at line " << line_count+1 << " in file " << examples_filename << std::endl;
				return( EXIT_FAILURE );
			}

			// Examples should be normalized so that every feature lies in [-1,1]
			if (curr_value < -1 || curr_value > 1) {
				std::cerr << "Feature number " << curr_index << " exceeds 1 in absolute value at line " << line_count+1 << " in file " << examples_filename << std::endl;
				return( EXIT_FAILURE );
			}
		
			curr_feature.push_back(IndexValuePair(curr_index,curr_value));
		}

	}

	if (line_count < num_features) {
		std::cerr << "Examples file ended before all features were read" << std::endl;
		return( EXIT_FAILURE );
	}

	examples_file.close();


	// open labels file
	std::ifstream labels_file(labels_filename.c_str());
	if (!labels_file.good()) {
		std::cerr << "Error reading " << labels_filename << std::endl;
		return( EXIT_FAILURE );
	}

	double curr_label;
	int label_count=0;

	// read labels file

	while (getline(labels_file,buffer)) {
		std::istringstream istr3(buffer);
		istr3 >> curr_label;
		label_count++;

		labels.push_back(curr_label);
	}

	if (label_count != num_examples) {
		std::cerr << "File " << labels_filename << " has incorrect number of labels" << std::endl;
		return( EXIT_FAILURE );
	}

	labels_file.close();


	// Run the stochastic coordinate descent (SCD) algorithm
	std::cout << "t j wj_old gj eta wj_new w_norm w_nonzero loss reg_loss" << std::endl;

	// Initialize weight vector to zero
	std::vector<double> w(num_features,0);
	std::vector<double> innerprod_with_w(num_examples,0);

	double sum, g, eta;
	int i;

	for (int iter=0; iter < num_iters; ++iter) {
		
		// update w

#ifdef _GREEDY_
		// choose feature with maximum guaranteed descent
		int best_i;
		double eta_best_i;	

		double predicted_descent;
		double best_descent = -1;

		for (i=0; i<num_features; i++) {

			sum=0;
			for (int j=0; j < examples[i].size(); j++) {
				IndexValuePair ivpair = examples[i][j];
				curr_index = ivpair.first;
				curr_value = ivpair.second;

				sum += L.loss_grad(innerprod_with_w[curr_index], labels[curr_index]) * curr_value;
			}

			num_accesses += examples[i].size();

			g = sum / num_examples;

			if (w[i] - g / L.rho > lambda / L.rho) 
				eta = -g / L.rho - lambda / L.rho;
			else {
				if (w[i] - g / L.rho < -lambda / L.rho)
					eta = -g / L.rho + lambda / L.rho;
				else
					eta = -w[i];
			}
			
			predicted_descent = -eta*g - L.rho / 2 * eta * eta - lambda * fabs(w[i] + eta) + lambda * fabs(w[i]);

			if (predicted_descent > best_descent) {
				best_i = i;
				eta_best_i = eta;
				best_descent = predicted_descent;
			} 
		}

		i = best_i;
		eta = eta_best_i;
#else
#ifdef _CYCLIC_
		// choose feature to update in a cyclic manner
		i = iter % num_features;
#else
		// choose a feature i randomly to update
		i = rand() % num_features;
#endif
		std::cout << iter << ' ' << i << ' ' << w[i] << ' ';

		sum=0;
		for (int j=0; j < examples[i].size(); j++) {
			IndexValuePair ivpair = examples[i][j];
			curr_index = ivpair.first;
			curr_value = ivpair.second;

			sum += L.loss_grad(innerprod_with_w[curr_index], labels[curr_index]) * curr_value;
		}

		g = sum / num_examples;
		std::cout << g << ' ';

		if (w[i] - g / L.rho > lambda / L.rho) 
			eta = -g / L.rho - lambda / L.rho;
		else {
			if (w[i] - g / L.rho < -lambda / L.rho)
				eta = -g / L.rho + lambda / L.rho;
			else
				eta = -w[i];
		}
#endif

		// update weight vector w
		w[i] = w[i] + eta;
		std::cout << eta << ' ' << w[i] << ' ';

		// update inner products with w
		for (int j=0; j < examples[i].size(); j++) {
			IndexValuePair ivpair = examples[i][j];
			curr_index = ivpair.first;
			curr_value = ivpair.second;

			innerprod_with_w[curr_index] = innerprod_with_w[curr_index] + eta * curr_value;
		}
		print_summary(innerprod_with_w, labels, w, num_examples, lambda, L);
	}

	return( EXIT_SUCCESS );
}
