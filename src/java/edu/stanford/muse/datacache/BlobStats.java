/*
 Copyright (C) 2012 The Stanford MobiSocial Laboratory

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package edu.stanford.muse.datacache;

public class BlobStats {
	public long unique_data_size;
	public long total_data_size;
	public long n_unique_pics;
	public long n_total_pics;

	public BlobStats(long unique_data_size, long total_data_size,
			long n_unique_pics, long n_total_pics) {
		this.unique_data_size = unique_data_size;
		this.total_data_size = total_data_size;
		this.n_unique_pics = n_unique_pics;
		this.n_total_pics = n_total_pics;
	}
}