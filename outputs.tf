output "neo4j_public_ip" {
  value = aws_instance.neo4j_instance.public_ip
}

output "neo4j_uri" {
  value = "bolt://${aws_instance.neo4j_instance.public_ip}:7687"
}