library("igraph")

edges<-scan('edges.txt', what=list(integer(), integer(), integer()), sep="\t",flush=TRUE)
nodes<-scan('nodes.txt', what=list(character(), character()), sep="\t", flush=TRUE)

e1 = unlist(edges[1])
e2 = unlist(edges[2])
wts = unlist(edges[3])

gr<-graph.empty(directed=FALSE)

gr<-add.vertices(gr, length(unlist(nodes[1])), name=unlist(nodes[1]), fullname=unlist(nodes[2]))

for(i in 1:length(e1)){
	gr<-add.edges(gr, c(e1[i], e2[i]), weight=wts[i])
	}
	
fg<-fastgreedy.community(gr,weights=E(gr)$weight)
bestcut<-which(fg$modularity == max(fg$modularity))
members<-community.to.membership(gr, fg$merges, bestcut - 1)

sink("q_scores_and_merges.txt")
print("Merges:")
print(fg$merges)
print("Modularity of merges:")
print(fg$modularity)
sink()

sink("testoutput.dunbar.txt")
for(i in 1:length(members$csize) - 1){
	print(which(members$membership == i))
	}
sink()

### Dumping lots of output... inefficient: we basically re-run the algorithm for each step
sink("clustering_dump.txt")
for(i in 1:(bestcut - 1)){
	gr3<-graph.empty()
	gr3<-gr
	members_tmp<-community.to.membership(gr3, fg$merges, i)
	print("")
	print(paste("Merge#:", " ", i))
	print(paste("Q-Score:", " ", fg$modularity[i]))
	for(j in 1:length(members$csize) - 1){
	print(which(members_tmp$membership == j))
	}
}
sink()

### This outputs a graphical representation of community merging for every time step
# l<-layout.fruchterman.reingold(gr)
# for(i in 1:(bestcut - 1)){
# 	gr3<-graph.empty()
# 	gr3<-gr
# 	members_tmp<-community.to.membership(gr3, fg$merges, i)
# 
# 	V(gr)$color<-"white"
# 	cols_tmp<-rainbow(length(which(members_tmp$csize > 1)))
# 	
# 	for(j in 1:(length(members_tmp$csize))){
# 		if(members_tmp$csize[j] > 1){
# 			V(gr)[which(members_tmp$membership == (j -1)) -1]$color <- cols_tmp[j]
# 		}
# 	}
# 
# 	fname<-paste("community_merge_", i, ".jpg", sep="")
# 	jpeg(fname)
# 	plot(gr, layout=l, vertex.size=8)
# 	#plot(gr, layout=l, vertex.size=8, vertex.label=V(gr)$name, vertex.label.cex=0.6, vertex.label.dist=0.2)
# 	dev.off()
# }
# 
# ### plotting the graph: comment out to stop!
# #cols<-rainbow(length(members$csize))
# #for(i in 1:(length(cols) - 1)){
# #	V(gr)[which(members$membership == i) -1]$color <- cols[i]
# #	}
# #plot(gr, layout=layout.fruchterman.reingold, vertex.size=8, vertex.label=fullname)
