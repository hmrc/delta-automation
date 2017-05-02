
# delta-automation

[![Build Status](https://travis-ci.org/hmrc/delta-automation.svg?branch=master)](https://travis-ci.org/hmrc/delta-automation) [ ![Download](https://api.bintray.com/packages/hmrc/releases/delta-automation/images/download.svg) ](https://bintray.com/hmrc/releases/delta-automation/_latestVersion)

This is a placeholder README.md for a new repository

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").

This application takes a spreadsheet and returns a text file containing the data from the ETMP delta ready to be loaded through the endpoint.

1. To use this application you need a few things:

   1. An input folder in which to place the .xlsx or .xls
   files to be processed.
        
   2. An output folder where ether output records will be kept.
        
   3. An output folder to store the files containing the records which could not be parsed.
        
   4. Configure the above information in delta-automation/conf/application.conf
        
        
2. Place the files to be processed in the input folder and enter the following:

   ./delta-automation

   The files produced by this will be of the original name with a time stamp appended to them.

   The files would be produced in the output folders

