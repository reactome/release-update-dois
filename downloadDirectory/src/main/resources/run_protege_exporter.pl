#! /usr/bin/perl

use GKB::WebUtils;

my $wu = GKB::WebUtils->new_from_cgi();
my $id = @ARGV[0];
my $file_name = @ARGV[1];

$wu->create_protege_project_wo_orthologues($file_name,[$id]);