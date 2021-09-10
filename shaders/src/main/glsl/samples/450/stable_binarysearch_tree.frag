#version 450

/*
 * Copyright 2019 The GraphicsFuzz Project Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

precision highp float;
precision highp int;

/*
This shader implements a binary search tree using an array
data structure. The elements of the tree are kept in an array
that contains a list of BST objects, each of which holds the
indices of left and right subrtrees in the array.

- Tree representation of the data used in this shader:
           9
        /    \
       5      12
     /  \      \
    2   7      15
       / \    /  \
      6  8   13  17
- Array representation:
  [9, 5, 12, 15, 7, 8, 2, 6, 17, 13]

After the tree is built, values 0..19 are searched
in it. For every value that should be in the tree
that is found, and for each value that should not
be in the tree that is not found, checksum is
incremented.

If the checksum matches with what we expect, the
image will be red, and if not, the image will be blue.

Screen coordinates do not matter for this shader;
the exact same computation is done for all pixels.

All computation is performed using integers, so the
shader is numerically stable.
*/

layout(location = 0) out vec4 _GLF_color;

struct BST {
    int data;
    int leftIndex;
    int rightIndex;
};

BST tree[10];

void makeTreeNode(inout BST tree, int data)
{
    tree.data = data;
    tree.leftIndex = -1;
    tree.rightIndex = -1;
}

void insert(int treeIndex, int data)
{
    int baseIndex = 0;
    while (baseIndex <= treeIndex) {
        // If new value is smaller thatn the current node, we known that we will have
        // add this element in the left side.
        if (data <= tree[baseIndex].data) {
          // If a left subtree of the current node is empty, the new node is added as
          // a left subtree of the current node.
          if (tree[baseIndex].leftIndex == -1) {
              tree[baseIndex].leftIndex = treeIndex;
              makeTreeNode(tree[treeIndex], data);
              return;
          } else {
              baseIndex = tree[baseIndex].leftIndex;
              continue;
          }
        } else {
            // If a right subtree of the current node is empty, the new node is added as
            // a right subtree of the current node.
            if (tree[baseIndex].rightIndex == -1) {
                tree[baseIndex].rightIndex = treeIndex;
                makeTreeNode(tree[treeIndex], data);
                return;
            } else {
                baseIndex = tree[baseIndex].rightIndex;
                continue;
            }
        }
    }
}

// Return element data if the given target exists in a tree. Otherwise, we simply return -1.
int search(int target) {
    BST currentNode;
    int index = 0;
    while (index != -1) {
        currentNode = tree[index];
        if (currentNode.data == target) {
            return target;
        }
        index = target > currentNode.data ? currentNode.rightIndex : currentNode.leftIndex;
    }
    return -1;
}

void main() {
    int treeIndex = 0;
    // Initialize root node.
    makeTreeNode(tree[0], 9);
    // Each time we insert a new node into the tree, we increment one.
    treeIndex++;

    insert(treeIndex, 5);
    treeIndex++;
    insert(treeIndex, 12);
    treeIndex++;
    insert(treeIndex, 15);
    treeIndex++;
    insert(treeIndex, 7);
    treeIndex++;
    insert(treeIndex, 8);
    treeIndex++;
    insert(treeIndex, 2);
    treeIndex++;
    insert(treeIndex, 6);
    treeIndex++;
    insert(treeIndex, 17);
    treeIndex++;
    insert(treeIndex, 13);

    int count = 0;
    for (int i = 0; i < 20; i++) {
        int result = search(i);
        switch (i) {
            case 9:
            case 5:
            case 12:
            case 15:
            case 7:
            case 8:
            case 2:
            case 6:
            case 17:
            case 13:
                if (result == i) {
                    count++;
                }
                break;
            default:
                if (result == -1) {
                    count++;
                }
                break;
        }
    }
    if (count == 20) {
        _GLF_color = vec4(1.0, 0.0, 0.0, 1.0);
    } else {
        _GLF_color = vec4(0.0, 0.0, 1.0, 1.0);
    }
}

