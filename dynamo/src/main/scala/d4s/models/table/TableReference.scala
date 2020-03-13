package d4s.models.table

import d4s.codecs.circe.{DynamoAttributeEncoder, DynamoEncoder}
import d4s.codecs.DynamoKeyAttribute
import d4s.config.DynamoMeta
import d4s.models.query.DynamoQuery
import d4s.models.query.DynamoRequest.BatchWriteEntity
import d4s.models.query.requests._
import d4s.models.table.TablePrefix.NamedPrefix
import d4s.models.table.index.TableIndex
import net.playq.aws.tagging.{AwsNameSpace, SharedTags}
import software.amazon.awssdk.services.dynamodb.model._

final case class TableReference(
  tableName: String,
  nameSpace: AwsNameSpace,
  key: DynamoKey[_, _],
  ttlField: Option[String],
  prefix: Option[NamedPrefix]
) {
  def fullName: String = {
    val renderedPrefix = prefix.fold("")(p => s"${p.prefix}-")
    s"$nameSpace$renderedPrefix$tableName"
  }

  def tags: Map[String, String] = prefix.map(_.toTag).getOrElse(Map.empty)

  def withTTL(ttlField: String): TableReference = copy(ttlField = Some(ttlField))

  def withPrefix[TP: TablePrefix](newPrefix: TP): TableReference = copy(prefix = Some(TablePrefix[TP].asTablePrefix(newPrefix)))
}

object TableReference {

  def apply(
    tableName: String,
    key: DynamoKey[_, _],
    ttlField: Option[String]    = None,
    prefix: Option[NamedPrefix] = None
  )(implicit dynamoMeta: DynamoMeta): TableReference = {
    new TableReference(
      tableName = tableName,
      nameSpace = dynamoMeta.nameSpace,
      key       = key,
      ttlField  = ttlField,
      prefix    = prefix
    )
  }

  def apply[H: DynamoKeyAttribute: DynamoAttributeEncoder](
    tableName: String
  )(hashName: String)(
    implicit dynamoMeta: DynamoMeta
  ): TableReference = {
    TableReference(
      tableName = tableName,
      key       = DynamoKey[H](hashName)
    )
  }

  def apply[H: DynamoKeyAttribute: DynamoAttributeEncoder, R: DynamoKeyAttribute: DynamoAttributeEncoder](
    tableName: String
  )(hashName: String, rangeName: String)(
    implicit dynamoMeta: DynamoMeta
  ): TableReference = {
    TableReference(
      tableName = tableName,
      key       = DynamoKey[H, R](hashName, rangeName)
    )
  }

  implicit final class TableReferenceOps(private val table: TableReference) extends AnyVal {
    def getItem: DynamoQuery[GetItem, GetItemResponse]                                     = GetItem(table).toQuery
    def getItem[Item: DynamoEncoder](keyItem: Item): DynamoQuery[GetItem, GetItemResponse] = GetItem(table).toQuery.withKeyItem(keyItem)
    def getItem(key: Map[String, AttributeValue]): DynamoQuery[GetItem, GetItemResponse]   = GetItem(table).toQuery.withKey(key)

    def putItem: DynamoQuery[PutItem, PutItemResponse]                                   = PutItem(table).toQuery
    def putItem[Item: DynamoEncoder](item: Item): DynamoQuery[PutItem, PutItemResponse]  = PutItem(table).toQuery.withItem(item)
    def putItem(key: Map[String, AttributeValue]): DynamoQuery[PutItem, PutItemResponse] = PutItem(table).toQuery.withItemAttributeValues(key)

    def deleteItem: DynamoQuery[DeleteItem, DeleteItemResponse]                                     = DeleteItem(table).toQuery
    def deleteItem[Item: DynamoEncoder](keyItem: Item): DynamoQuery[DeleteItem, DeleteItemResponse] = DeleteItem(table).toQuery.withKeyItem(keyItem)
    def deleteItem(key: Map[String, AttributeValue]): DynamoQuery[DeleteItem, DeleteItemResponse]   = DeleteItem(table).toQuery.withKey(key)

    def updateItem: DynamoQuery[UpdateItem, UpdateItemResponse]                                   = UpdateItem(table).toQuery
    def updateItem[Item: DynamoEncoder](item: Item): DynamoQuery[UpdateItem, UpdateItemResponse]  = UpdateItem(table).toQuery.withItem(item)
    def updateItem(key: Map[String, AttributeValue]): DynamoQuery[UpdateItem, UpdateItemResponse] = UpdateItem(table).toQuery.withItemAttributeValues(key)

    def deleteItemBatch: DynamoQuery[DeleteItemBatch, List[BatchWriteItemResponse]] = DeleteItemBatch(table).toQuery
    def deleteItemBatch[I: DynamoEncoder](deleteBatch: List[I]): DynamoQuery[DeleteItemBatch, List[BatchWriteItemResponse]] =
      DeleteItemBatch(table).toQuery.withBatch(deleteBatch)

    def putItemBatch: DynamoQuery[PutItemBatch, List[BatchWriteItemResponse]] = PutItemBatch(table).toQuery
    def putItemBatch[I: DynamoEncoder](putBatch: List[BatchWriteEntity[I]]): DynamoQuery[PutItemBatch, List[BatchWriteItemResponse]] =
      PutItemBatch(table).toQuery.withBatch(putBatch)

    def getItemBatch: DynamoQuery[GetItemBatch, List[BatchGetItemResponse]]                                      = GetItemBatch(table).toQuery
    def getItemBatch[I: DynamoEncoder](getBatch: List[I]): DynamoQuery[GetItemBatch, List[BatchGetItemResponse]] = GetItemBatch(table).toQuery.withBatch(getBatch)

    def scan: DynamoQuery[Scan, ScanResponse]                          = Scan(table).toQuery
    def scan(index: TableIndex[_, _]): DynamoQuery[Scan, ScanResponse] = Scan(table).withIndex(index).toQuery

    def update: DynamoQuery[UpdateTable, UpdateTableResponse]                                                         = UpdateTable(table).toQuery
    def describe: DynamoQuery[DescribeTable, DescribeTableResponse]                                                   = DescribeTable(table).toQuery
    def updateTags(arn: String, tagsToUpdate: Map[String, String]): DynamoQuery[UpdateTableTags, TagResourceResponse] = UpdateTableTags(table, arn, tagsToUpdate).toQuery
    def markForDeletion(arn: String): DynamoQuery[UpdateTableTags, TagResourceResponse]                               = UpdateTableTags(table, arn, Map(SharedTags.markedForDeletion)).toQuery

    def query: DynamoQuery[Query, QueryResponse]                                                            = Query(table).toQuery
    def query(index: TableIndex[_, _]): DynamoQuery[Query, QueryResponse]                                   = Query(table).withIndex(index).toQuery
    def query(key: Map[String, AttributeValue]): DynamoQuery[Query, QueryResponse]                          = Query(table).withKey(key).toQuery
    def query(index: TableIndex[_, _], key: Map[String, AttributeValue]): DynamoQuery[Query, QueryResponse] = Query(table).withIndex(index).withKey(key).toQuery

    def queryDeleteBatch: DynamoQuery[QueryDeleteBatch, List[BatchWriteItemResponse]]                          = QueryDeleteBatch(table).toQuery
    def queryDeleteBatch(maxParallelDeletes: Int): DynamoQuery[QueryDeleteBatch, List[BatchWriteItemResponse]] = QueryDeleteBatch(table, Some(maxParallelDeletes)).toQuery
    def queryDeleteBatch(index: TableIndex[_, _]): DynamoQuery[QueryDeleteBatch, List[BatchWriteItemResponse]] =
      QueryDeleteBatch(table).withIndex(index).toQuery
    def queryDeleteBatch(key: Map[String, AttributeValue]): DynamoQuery[QueryDeleteBatch, List[BatchWriteItemResponse]] =
      QueryDeleteBatch(table).withKey(key).toQuery
    def queryDeleteBatch(index: TableIndex[_, _], key: Map[String, AttributeValue]): DynamoQuery[QueryDeleteBatch, List[BatchWriteItemResponse]] =
      QueryDeleteBatch(table).withIndex(index).withKey(key).toQuery
  }
}
